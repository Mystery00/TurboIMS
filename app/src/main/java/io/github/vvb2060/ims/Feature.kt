package io.github.vvb2060.ims

enum class Feature(
    val key: String,
    val valueType: FeatureValueType,
    val showTitleRes: Int,
    val showDescriptionRes: Int,
) {
    CARRIER_NAME(
        "carrier_name",
        FeatureValueType.STRING,
        R.string.carrier_name,
        R.string.carrier_name_desc,
    ),
    VOLTE(
        "volte",
        FeatureValueType.BOOLEAN,
        R.string.volte,
        R.string.volte_desc,
    ),
    VOWIFI(
        "vowifi",
        FeatureValueType.BOOLEAN,
        R.string.vowifi,
        R.string.vowifi_desc,
    ),
    VT(
        "vt",
        FeatureValueType.BOOLEAN,
        R.string.vt,
        R.string.vt_desc,
    ),
    VONR(
        "vonr",
        FeatureValueType.BOOLEAN,
        R.string.vonr,
        R.string.vonr_desc,
    ),
    CROSS_SIM(
        "cross_sim",
        FeatureValueType.BOOLEAN,
        R.string.cross_sim,
        R.string.cross_sim_desc,
    ),
    UT(
        "ut",
        FeatureValueType.BOOLEAN,
        R.string.ut,
        R.string.ut_desc,
    ),
    FIVE_G_NR(
        "5g_nr",
        FeatureValueType.BOOLEAN,
        R.string._5g_nr,
        R.string._5g_nr_desc,
    ),
}

enum class FeatureValueType {
    BOOLEAN, STRING,
}