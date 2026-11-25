package com.notremock.app.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.notremock.app.data.model.City
import java.io.BufferedReader
import java.io.InputStreamReader

class CityDatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cities (
                id INTEGER PRIMARY KEY,
                name_en TEXT NOT NULL,
                name_fa TEXT NOT NULL,
                admin1_en TEXT NOT NULL,
                country_code TEXT NOT NULL,
                country_en TEXT NOT NULL,
                country_fa TEXT NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                timezone TEXT NOT NULL,
                is_capital INTEGER NOT NULL,
                population INTEGER NOT NULL,
                search_norm TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_cities_capital_pop ON cities(is_capital DESC, population DESC)")
        db.execSQL("CREATE INDEX idx_cities_search ON cities(search_norm)")
        importFromAssets(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS cities")
        onCreate(db)
    }

    private fun importFromAssets(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                """
                INSERT OR REPLACE INTO cities (
                    id, name_en, name_fa, admin1_en, country_code, country_en, country_fa,
                    lat, lng, timezone, is_capital, population, search_norm
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            context.assets.open("cities.tsv").use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { raw ->
                        if (raw.isBlank()) return@forEach
                        val p = raw.split('\t')
                        if (p.size < 12) return@forEach
                        val id = p[0].toLongOrNull() ?: return@forEach
                        val nameEn = p[1]
                        val nameFa = p[2]
                        val admin1En = p[3]
                        val cc = p[4]
                        val countryEn = p[5]
                        val countryFa = p[6]
                        val lat = p[7].toDoubleOrNull() ?: return@forEach
                        val lng = p[8].toDoubleOrNull() ?: return@forEach
                        val tz = p[9]
                        val isCap = p[10].toIntOrNull() ?: 0
                        val pop = p[11].toLongOrNull() ?: 0L
                        val searchNorm = normalizeSearch("$nameEn $nameFa $admin1En $countryEn $countryFa $cc")

                        stmt.clearBindings()
                        stmt.bindLong(1, id)
                        stmt.bindString(2, nameEn)
                        stmt.bindString(3, nameFa)
                        stmt.bindString(4, admin1En)
                        stmt.bindString(5, cc)
                        stmt.bindString(6, countryEn)
                        stmt.bindString(7, countryFa)
                        stmt.bindDouble(8, lat)
                        stmt.bindDouble(9, lng)
                        stmt.bindString(10, tz)
                        stmt.bindLong(11, isCap.toLong())
                        stmt.bindLong(12, pop)
                        stmt.bindString(13, searchNorm)
                        stmt.executeInsert()
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getTotalCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM cities", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getCityById(id: Long): City? {
        readableDatabase.rawQuery("SELECT * FROM cities WHERE id = ? LIMIT 1", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) readCity(c) else null
        }
    }

    fun getCitiesByIds(ids: List<Long>): List<City> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val args = ids.map { it.toString() }.toTypedArray()
        val found = mutableMapOf<Long, City>()
        readableDatabase.rawQuery("SELECT * FROM cities WHERE id IN ($placeholders)", args).use { c ->
            while (c.moveToNext()) {
                val city = readCity(c)
                found[city.id] = city
            }
        }
        return ids.mapNotNull { found[it] }
    }

    fun searchCities(query: String, limit: Int = 80): List<City> {
        val norm = normalizeSearch(query)
        val sql: String
        val args: Array<String>
        if (norm.isBlank()) {
            sql = "SELECT * FROM cities ORDER BY is_capital DESC, population DESC LIMIT ?"
            args = arrayOf(limit.toString())
        } else {
            val tokens = norm.split(' ').filter { it.isNotBlank() }.take(4)
            val where = tokens.joinToString(" AND ") { "search_norm LIKE ?" }
            sql = """
                SELECT * FROM cities
                WHERE $where
                ORDER BY
                    CASE WHEN search_norm LIKE ? THEN 0 ELSE 1 END,
                    is_capital DESC,
                    population DESC
                LIMIT ?
            """.trimIndent()
            val argList = tokens.map { "%$it%" }.toMutableList()
            argList.add("${tokens.first()}%")
            argList.add(limit.toString())
            args = argList.toTypedArray()
        }

        val result = ArrayList<City>(limit)
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                result.add(readCity(c))
            }
        }
        return result
    }

    private fun readCity(c: Cursor): City = City(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        nameEn = c.getString(c.getColumnIndexOrThrow("name_en")),
        nameFa = c.getString(c.getColumnIndexOrThrow("name_fa")),
        admin1En = c.getString(c.getColumnIndexOrThrow("admin1_en")),
        countryCode = c.getString(c.getColumnIndexOrThrow("country_code")),
        countryEn = c.getString(c.getColumnIndexOrThrow("country_en")),
        countryFa = c.getString(c.getColumnIndexOrThrow("country_fa")),
        lat = c.getDouble(c.getColumnIndexOrThrow("lat")),
        lng = c.getDouble(c.getColumnIndexOrThrow("lng")),
        timeZoneId = c.getString(c.getColumnIndexOrThrow("timezone")),
        isCapital = c.getInt(c.getColumnIndexOrThrow("is_capital")) == 1,
        population = c.getLong(c.getColumnIndexOrThrow("population"))
    )

    companion object {
        private const val DB_NAME = "notremock_cities.db"
        private const val DB_VERSION = 1

        fun normalizeSearch(raw: String): String {
            return raw.trim()
                .lowercase()
                .replace('ي', 'ی')
                .replace('ى', 'ی')
                .replace('ك', 'ک')
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ة', 'ه')
                .replace('\u200c', ' ')
                .replace(Regex("\\s+"), " ")
        }
    }
}
