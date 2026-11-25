package com.notremock.app.data.model

data class SimProfile(
    val mcc: String,
    val mnc: String,
    val operatorName: String,
    val countryIso: String
)

object MccTable {
    private val map = mapOf(
        "IR" to SimProfile("432", "11", "IR-MCI", "ir"),
        "US" to SimProfile("310", "410", "AT&T", "us"),
        "GB" to SimProfile("234", "15", "Vodafone UK", "gb"),
        "DE" to SimProfile("262", "01", "Telekom.de", "de"),
        "FR" to SimProfile("208", "01", "Orange F", "fr"),
        "NL" to SimProfile("204", "08", "KPN", "nl"),
        "CH" to SimProfile("228", "01", "Swisscom", "ch"),
        "SE" to SimProfile("240", "01", "Telia S", "se"),
        "NO" to SimProfile("242", "01", "Telenor", "no"),
        "DK" to SimProfile("238", "01", "TDC", "dk"),
        "FI" to SimProfile("244", "91", "Telia FI", "fi"),
        "AT" to SimProfile("232", "01", "A1", "at"),
        "IT" to SimProfile("222", "01", "TIM", "it"),
        "ES" to SimProfile("214", "07", "Movistar", "es"),
        "PT" to SimProfile("268", "06", "MEO", "pt"),
        "PL" to SimProfile("260", "01", "Plus", "pl"),
        "CA" to SimProfile("302", "720", "Rogers", "ca"),
        "AU" to SimProfile("505", "01", "Telstra", "au"),
        "JP" to SimProfile("440", "10", "NTT DOCOMO", "jp"),
        "KR" to SimProfile("450", "05", "SK Telecom", "kr"),
        "SG" to SimProfile("525", "01", "Singtel", "sg"),
        "AE" to SimProfile("424", "02", "Etisalat", "ae"),
        "TR" to SimProfile("286", "01", "Turkcell", "tr"),
        "SA" to SimProfile("420", "01", "STC", "sa"),
        "QA" to SimProfile("427", "01", "Ooredoo", "qa"),
        "RU" to SimProfile("250", "01", "MTS", "ru"),
        "UA" to SimProfile("255", "01", "Vodafone UA", "ua"),
        "IN" to SimProfile("404", "45", "Airtel", "in"),
        "BR" to SimProfile("724", "05", "Claro", "br"),
        "HK" to SimProfile("454", "00", "CSL", "hk"),
        "TW" to SimProfile("466", "92", "Chunghwa", "tw"),
        "MY" to SimProfile("502", "12", "Maxis", "my"),
        "ID" to SimProfile("510", "10", "Telkomsel", "id"),
        "TH" to SimProfile("520", "01", "AIS", "th"),
        "IQ" to SimProfile("418", "20", "Zain IQ", "iq"),
        "AM" to SimProfile("283", "01", "Beeline AM", "am"),
        "GE" to SimProfile("282", "01", "Geocell", "ge"),
        "AZ" to SimProfile("400", "01", "Azercell", "az"),
        "OM" to SimProfile("422", "02", "Omantel", "om")
    )

    fun forCountry(countryCode: String): SimProfile {
        val upper = countryCode.uppercase()
        return map[upper] ?: SimProfile(
            mcc = "310",
            mnc = "00",
            operatorName = "Carrier",
            countryIso = countryCode.lowercase()
        )
    }
}
