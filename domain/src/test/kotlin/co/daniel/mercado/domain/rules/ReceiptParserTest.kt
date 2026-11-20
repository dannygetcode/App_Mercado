package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptParserTest {

    @Test
    fun `recibo con nombre y valor en el mismo renglon, impuesto y total`() {
        val scan = ReceiptParser.parse(
            listOf(
                "ALMACENES EXITO S.A.",
                "NIT 890.900.608-9",
                "7702001041183 ARROZ DIANA 1000G  5.900 A",
                "LECHE ALQUERIA ENT 1100ML  4.300 E",
                "PAN TAJADO BIMBO  $ 7.250",
                "SUBTOTAL  17.450",
                "IVA 19%  1.100",
                "TOTAL A PAGAR  $ 17.450",
                "EFECTIVO  20.000",
                "CAMBIO  2.550",
            ),
        )

        assertEquals(
            listOf(
                ReceiptLine("ARROZ DIANA 1000G", 1, Pesos(5_900), "7702001041183"),
                ReceiptLine("LECHE ALQUERIA ENT 1100ML", 1, Pesos(4_300)),
                ReceiptLine("PAN TAJADO BIMBO", 1, Pesos(7_250)),
            ),
            scan.lines,
        )
        assertEquals(Pesos(17_450), scan.total)
    }

    @Test
    fun `cantidad en un renglon aparte se une al nombre de arriba`() {
        val scan = ReceiptParser.parse(
            listOf(
                "GASEOSA POSTOBON 400ML",
                "  3 X 2.500        7.500",
                "HUEVOS AA X30",
                "1 UN X 16.900  16.900",
                "TOTAL  24.400",
            ),
        )

        assertEquals(
            listOf(
                ReceiptLine("GASEOSA POSTOBON 400ML", 3, Pesos(7_500)),
                ReceiptLine("HUEVOS AA X30", 1, Pesos(16_900)),
            ),
            scan.lines,
        )
        assertEquals(Pesos(2_500), scan.lines[0].unitPrice)
        assertEquals(Pesos(24_400), scan.total)
    }

    @Test
    fun `el descuento se resta al producto anterior`() {
        val scan = ReceiptParser.parse(
            listOf(
                "CAFE SELLO ROJO 500G  21.500",
                "DESCUENTO  -2.000",
                "AZUCAR MANUELITA  4.800",
            ),
        )

        assertEquals(Pesos(19_500), scan.lines[0].amount)
        assertEquals(2, scan.lines.size)
    }

    @Test
    fun `un numero pegado al nombre no es un precio`() {
        val scan = ReceiptParser.parse(listOf("ARROZ ROA 500", "LECHE 1100ML", "CAJA DE HUEVOS X12  13.900"))

        assertEquals(listOf(ReceiptLine("CAJA DE HUEVOS X12", 1, Pesos(13_900))), scan.lines)
    }

    @Test
    fun `valor sin separador cuenta solo en su propia columna`() {
        val scan = ReceiptParser.parse(listOf("PAPA CRIOLLA KG  0,850 KG X 6.000   5100"))

        assertEquals(1, scan.lines.size)
        assertEquals(Pesos(5_100), scan.lines[0].amount)
        assertTrue(scan.lines[0].name.startsWith("PAPA CRIOLLA"))
    }

    @Test
    fun `encabezados, pagos y datos legales no son productos`() {
        val scan = ReceiptParser.parse(
            listOf(
                "TIENDAS ARA",
                "Resolucion DIAN 18764000001  2024",
                "Cajero: MARIA  001",
                "Fecha 20/11/2026 Hora 10:32",
                "Tarjeta debito  45.000",
                "Total items 12",
                "Gracias por su compra",
            ),
        )

        assertTrue(scan.lines.isEmpty())
        assertNull(scan.total)
    }

    @Test
    fun `renglones del mismo cuadro llegan juntos aunque el valor sea otro bloque`() {
        val rows = ReceiptRows.group(
            listOf(
                TextBox("LECHE ALQUERIA", 10, 100, 300, 130),
                TextBox("4.300", 600, 102, 680, 128),
                TextBox("ARROZ DIANA", 10, 140, 260, 170),
                TextBox("5.900", 600, 143, 680, 168),
            ),
        )

        assertEquals(listOf("LECHE ALQUERIA  4.300", "ARROZ DIANA  5.900"), rows)
    }

    @Test
    fun `parecido de nombres entre recibo y catalogo`() {
        assertTrue(NameSimilarity.of("ARROZ DIANA X 1000G", "Arroz Diana 1 kg") > 60)
        assertTrue(NameSimilarity.of("LECHE ALQUERIA ENT", "Leche Colanta entera") < 55)
    }
}
