package com.mizan.app

import com.mizan.app.core.*
import org.junit.Assert.assertEquals
import org.junit.Test

private var fails = 0
private fun check(name: String, cond: Boolean, info: Any? = "") { if (!cond) { fails++; println("FAIL $name $info") } else println("ok   $name") }

fun runAllCoreChecks() {
    // Num
    check("1 1/2", Num.parse("1 1/2") == 1.5)
    check("½", Num.parse("½") == 0.5)
    check("1½", Num.parse("1½") == 1.5)
    check("0,5", Num.parse("0,5") == 0.5)
    check("1,000.5", Num.parse("1,000.5") == 1000.5)
    check("arabic ١٠٠٠", Num.parse("١٠٠٠") == 1000.0)
    check("arabic ١٫٥", Num.parse("١٫٥") == 1.5)
    check("3/4", Num.parse("3/4") == 0.75)
    check("1/0 invalid", Num.parse("1/0") == null)
    check("abc invalid", Num.parse("abc") == null)
    check("0 not positive", Num.parsePositive("0") == null)
    check("-3 not positive", Num.parsePositive("-3") == null)
    check("fmt 18.75", Num.pct(18.75) == "18.75", Num.pct(18.75))
    check("fmt 2400", Num.fmt(2400.0) == "2400")

    // Scaling example from brief
    val c = RecipeContent(ingredients = listOf(
        Ingredient(id="m", name="milk", amount=1000.0, unit="g"),
        Ingredient(id="c", name="cream", amount=300.0, unit="g"),
        Ingredient(id="s", name="sugar", amount=200.0, unit="g"),
        Ingredient(id="p", name="pistachio paste", amount=100.0, unit="g")))
    val t = Scaling.totals(c.ingredients)
    check("total 1600", t.totalG == 1600.0, t.totalG)
    val pc = Scaling.percentages(c)
    check("pct", pc["m"]==62.5 && pc["c"]==18.75 && pc["s"]==12.5 && pc["p"]==6.25, pc)
    val f = Scaling.factorFromRow(1000.0, 1500.0)!!
    check("factor 1.5", f == 1.5)
    check("scaled cream 450", Scaling.scaled(300.0, f) == 450.0)
    check("scaled total 2400", c.ingredients.sumOf { Scaling.scaled(it.amount, f)!! } == 2400.0)
    check("factor from total", Scaling.factorFromTotal(c.ingredients, 2400.0) == 1.5)
    check("zero baseline", Scaling.factorFromRow(0.0, 5.0) == null)
    check("zero input", Scaling.factorFromRow(100.0, 0.0) == null)
    // repeated scaling does not compound rounding: from baseline each time
    var ff = 1.0; repeat(50) { ff = Scaling.factorFromRow(1000.0, 1000.0 * (1 + it % 7) / 3.0)!! }
    check("no compounding", Scaling.scaled(300.0, Scaling.factorFromRow(1000.0, 1500.0)!!) == 450.0)
    // mixed units: no total
    val mixed = c.ingredients + Ingredient(name="vanilla", amount=1.0, unit="pc") + Ingredient(name="water", amount=100.0, unit="ml")
    check("mixed total null", Scaling.totals(mixed).totalG == null)
    check("mixed pct empty", Scaling.percentages(c.copy(ingredients = mixed)).isEmpty())
    val withDensity = mixed.map { if (it.unit=="ml") it.copy(densityGPerMl=1.0) else if (it.unit=="pc") it.copy(pieceWeightG=3.0) else it }
    check("density + piece weight total", Scaling.totals(withDensity).totalG == 1703.0, Scaling.totals(withDensity).totalG)
    check("cup ambiguous no grams", Scaling.grams(Ingredient(amount=1.0, unit="cup", densityGPerMl=1.0)) == null)
    check("oz weight", Units.convert(1.0,"oz","g") == 28.349523125)
    check("fl oz not weight", Units.convert(1.0,"floz_us","g") == null)
    check("us cup ml", Units.convert(1.0,"cup_us","ml") == 236.5882365)
    check("resolve cup imp", Units.resolve("cup", VolumeStd.IMPERIAL) == "cup_imp")
    // baker's %
    val bread = RecipeContent(pctBasis = PctBasis.INGREDIENT, pctBaseIngredientId = "f", ingredients = listOf(
        Ingredient(id="f", name="flour", amount=1000.0, unit="g"), Ingredient(id="w", name="water", amount=0.7, unit="kg")))
    check("bakers %", Scaling.percentages(bread)["w"]?.let { kotlin.math.abs(it-70.0) < 1e-9 } == true, Scaling.percentages(bread))

    // Temperature
    check("85C=185F", Temp.cToF(85.0) == 185.0)
    check("185F=85C", Temp.fToC(185.0) == 85.0)
    val s1 = Temp.convertText("heat until it reaches 82–85 °C (180–185 °F). Do not boil.", true)
    check("text range + paren", s1 == "heat until it reaches 180–185 °F. Do not boil.", s1)
    val s2 = Temp.convertText("Pasteurize at 85°C for 30 sec", true)
    check("text 85°C", s2 == "Pasteurize at 185 °F for 30 sec", s2)
    val s3 = Temp.convertText("سخّن حتى ٨٥ درجة مئوية ثم برّد", true)
    check("arabic temp", s3 == "سخّن حتى 185 درجة فهرنهايت ثم برّد", s3)
    val s4 = Temp.convertText("Bake at 350 °F for 20 minutes", false)
    check("F->C", s4 == "Bake at 176.7 °C for 20 minutes", s4)
    check("2 C cups not temp", Temp.convertText("add 2 C sugar", true) == "add 2 C sugar")
    check("find temp", Temp.find("heat to 82-85°C").firstOrNull()?.c == 85.0)

    // Conversion separate from scaling, original preserved
    val r = Converter.toPrefs(Ingredient(name="milk", amount=2.0, unit="cup_us"), UnitPrefs.metric())
    check("cup->ml", r.changed && r.ingredient.unit=="ml" && Num.approxEq(r.ingredient.amount, 473.176473) && r.ingredient.original?.unit=="cup_us", r)
    val r2 = Converter.toPrefs(Ingredient(name="milk", amount=2.0, unit="cup"), UnitPrefs.metric())
    check("ambiguous cup not converted", !r2.changed && r2.reason==Converter.Reason.AMBIGUOUS_STANDARD)
    val r3 = Converter.toPrefs(Ingredient(name="milk", amount=500.0, unit="ml"), UnitPrefs.metric().copy(volumeToWeight=true))
    check("no density stays volume", r3.ingredient.unit=="ml" && r3.reason==Converter.Reason.NEEDS_DENSITY, r3)
    val r4 = Converter.toPrefs(Ingredient(name="salt", amount=5.0, unit="ml"), UnitPrefs.us())
    check("us fit tsp", r4.ingredient.unit=="tsp_us", r4)

    // Parser
    val d = RecipeParser.parseText("""
Pistachio Gelato
Ingredients:
- Milk 1000 g
- 300g cream
- 1 1/2 cups sugar
- Pistachio paste: 100 g
- 2 eggs
- salt to taste
Method
1. Mix the ingredients: whisk the milk, cream and sugar.
2. Heat the mixture to 82–85 °C (180–185 °F).
Do not boil.
3. Cool, chill for at least 4 hours, then churn.
Notes
Use Sicilian pistachio.
""".trimIndent())
    check("title", d.title == "Pistachio Gelato", d.title)
    check("6 ingredients", d.ingredients.size == 6, d.ingredients.map{it.name})
    check("milk", d.ingredients[0].let { it.name=="Milk" && it.amount==1000.0 && it.unit=="g" }, d.ingredients[0])
    check("cream", d.ingredients[1].let { it.name=="cream" && it.amount==300.0 && it.unit=="g" }, d.ingredients[1])
    check("sugar cup ambiguous", d.ingredients[2].let { it.amount==1.5 && it.unit=="cup" && it.uncertain }, d.ingredients[2])
    check("paste", d.ingredients[3].let { it.name=="Pistachio paste" && it.amount==100.0 }, d.ingredients[3])
    check("eggs", d.ingredients[4].let { it.amount==2.0 && it.unit=="pc" && it.name=="eggs" }, d.ingredients[4])
    check("salt no qty flagged", d.ingredients[5].let { it.amount==null && it.uncertain }, d.ingredients[5])
    check("3 steps", d.steps.size==3, d.steps.map{it.text})
    check("step1 title", d.steps[0].title=="Mix the ingredients", d.steps[0])
    check("step2 temp 85", d.steps[1].tempC==85.0 && d.steps[1].text.contains("Do not boil"), d.steps[1])
    check("step3 minutes", d.steps[2].minutes==240.0, d.steps[2].minutes)
    check("notes", d.notes=="Use Sicilian pistachio.")
    check("ambiguous flag", d.hasAmbiguousUnits)

    val a = RecipeParser.parseText("""
جيلاتو الفستق
المكونات
حليب ١٠٠٠ غرام
٣٠٠ غ كريمة
سكر 200 غ
معجون الفستق 100 غم
طريقة التحضير
١. اخلط المكونات جيداً.
٢. سخّن الخليط حتى 85 درجة مئوية.
""".trimIndent())
    check("ar title", a.title=="جيلاتو الفستق", a.title)
    check("ar 4 ing", a.ingredients.size==4, a.ingredients)
    check("ar milk", a.ingredients[0].let { it.name=="حليب" && it.amount==1000.0 && it.unit=="g" }, a.ingredients[0])
    check("ar cream", a.ingredients[1].let { it.name=="كريمة" && it.amount==300.0 && it.unit=="g" }, a.ingredients[1])
    check("ar paste", a.ingredients[3].let { it.name=="معجون الفستق" && it.unit=="g" }, a.ingredients[3])
    check("ar steps", a.steps.size==2 && a.steps[1].tempC==85.0, a.steps)

    println(if (fails==0) "ALL PASSED" else "$fails FAILED")
}

class CoreLogicTest {
    @Test
    fun allCoreChecksPass() {
        runAllCoreChecks()
        assertEquals("core logic checks failed (see output)", 0, fails)
    }
}
