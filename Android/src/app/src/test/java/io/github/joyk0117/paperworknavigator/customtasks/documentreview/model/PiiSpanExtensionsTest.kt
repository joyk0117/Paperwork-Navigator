package io.github.joyk0117.paperworknavigator.customtasks.documentreview.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PiiSpanExtensionsTest {

    private fun span(category: String) = PiiSpan(id = "pii_01", spanText = "test", category = category)

    // ── categoryLabel: 各カテゴリの日本語ラベル（デフォルト） ────────────────────

    @Test fun categoryLabel_name_ja() = assertEquals("氏名", span("name").categoryLabel("ja"))
    @Test fun categoryLabel_address_ja() = assertEquals("住所", span("address").categoryLabel("ja"))
    @Test fun categoryLabel_phone_ja() = assertEquals("電話番号", span("phone").categoryLabel("ja"))
    @Test fun categoryLabel_account_ja() = assertEquals("口座番号", span("account").categoryLabel("ja"))
    @Test fun categoryLabel_dob_ja() = assertEquals("生年月日", span("dob").categoryLabel("ja"))
    @Test fun categoryLabel_idNumber_ja() = assertEquals("番号", span("id_number").categoryLabel("ja"))
    @Test fun categoryLabel_other_ja() = assertEquals("個人情報", span("other").categoryLabel("ja"))
    @Test fun categoryLabel_unknown_ja() = assertEquals("個人情報", span("unknown_cat").categoryLabel("ja"))

    // ── categoryLabel: 英語ラベル ────────────────────────────────────────────────

    @Test fun categoryLabel_name_en() = assertEquals("Name", span("name").categoryLabel("en"))
    @Test fun categoryLabel_address_en() = assertEquals("Address", span("address").categoryLabel("en"))
    @Test fun categoryLabel_phone_en() = assertEquals("Phone", span("phone").categoryLabel("en"))
    @Test fun categoryLabel_account_en() = assertEquals("Account", span("account").categoryLabel("en"))
    @Test fun categoryLabel_dob_en() = assertEquals("Date of Birth", span("dob").categoryLabel("en"))
    @Test fun categoryLabel_idNumber_en() = assertEquals("ID Number", span("id_number").categoryLabel("en"))
    @Test fun categoryLabel_other_en() = assertEquals("Personal Information", span("other").categoryLabel("en"))

    // ── categoryLabel: 中国語・韓国語・スペイン語・ヒンディー語・フランス語 ─────

    @Test fun categoryLabel_name_zh() = assertEquals("姓名", span("name").categoryLabel("zh"))
    @Test fun categoryLabel_name_ko() = assertEquals("이름", span("name").categoryLabel("ko"))
    @Test fun categoryLabel_name_es() = assertEquals("Nombre", span("name").categoryLabel("es"))
    @Test fun categoryLabel_name_hi() = assertEquals("नाम", span("name").categoryLabel("hi"))
    @Test fun categoryLabel_name_fr() = assertEquals("Nom", span("name").categoryLabel("fr"))

    @Test fun categoryLabel_dob_zh() = assertEquals("出生日期", span("dob").categoryLabel("zh"))
    @Test fun categoryLabel_dob_ko() = assertEquals("생년월일", span("dob").categoryLabel("ko"))
    @Test fun categoryLabel_dob_fr() = assertEquals("Date de naissance", span("dob").categoryLabel("fr"))

    // idNumber の fr はアポストロフィを含む
    @Test fun categoryLabel_idNumber_fr() = assertEquals("Numéro d'identification", span("id_number").categoryLabel("fr"))

    // ── categoryLabel: other カテゴリの全言語 ────────────────────────────────────

    @Test fun categoryLabel_other_zh() = assertEquals("个人信息", span("other").categoryLabel("zh"))
    @Test fun categoryLabel_other_ko() = assertEquals("개인정보", span("other").categoryLabel("ko"))
    @Test fun categoryLabel_other_es() = assertEquals("Información personal", span("other").categoryLabel("es"))
    @Test fun categoryLabel_other_hi() = assertEquals("व्यक्तिगत जानकारी", span("other").categoryLabel("hi"))
    @Test fun categoryLabel_other_fr() = assertEquals("Informations personnelles", span("other").categoryLabel("fr"))

    // ── categoryLabel: 未知の lang コードは日本語にフォールバック ────────────────

    @Test fun categoryLabel_name_unknownLang() = assertEquals("氏名", span("name").categoryLabel("unknown"))
    @Test fun categoryLabel_other_unknownLang() = assertEquals("個人情報", span("other").categoryLabel("unknown"))
}
