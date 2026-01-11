package com.mjc.mascotalink;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests unitarios para validación de calificaciones
 * No requiere emulador - lógica de rating
 */
public class RatingValidationTest {

    private double rating;

    @Before
    public void setUp() {
        rating = 0.0;
    }

    @Test
    public void testValidRating5Stars() {
        rating = 5.0;
        boolean isValid = rating >= 1.0 && rating <= 5.0;
        assertTrue("Rating 5.0 es válido", isValid);
    }

    @Test
    public void testValidRating1Star() {
        rating = 1.0;
        boolean isValid = rating >= 1.0 && rating <= 5.0;
        assertTrue("Rating 1.0 es válido", isValid);
    }

    @Test
    public void testValidRating3Point5Stars() {
        rating = 3.5;
        boolean isValid = rating >= 1.0 && rating <= 5.0;
        assertTrue("Rating 3.5 es válido", isValid);
    }

    @Test
    public void testInvalidRatingBelow1() {
        rating = 0.5;
        boolean isValid = rating >= 1.0 && rating <= 5.0;
        assertFalse("Rating 0.5 es inválido", isValid);
    }

    @Test
    public void testInvalidRatingAbove5() {
        rating = 5.5;
        boolean isValid = rating >= 1.0 && rating <= 5.0;
        assertFalse("Rating 5.5 es inválido", isValid);
    }

    @Test
    public void testAverageRating() {
        double[] ratings = {4.0, 3.5, 5.0, 4.5};
        double suma = 0;
        for (double r : ratings) {
            suma += r;
        }
        double promedio = suma / ratings.length;
        assertEquals("Promedio correcto", 4.25, promedio, 0.01);
    }

    @Test
    public void testRatingRounding() {
        double r = 4.333333;
        double redondeado = Math.round(r * 10.0) / 10.0;
        assertEquals("Redondeo a 1 decimal", 4.3, redondeado, 0.01);
    }

    @Test
    public void testFilterByMinimumRating() {
        double[] ratings = {4.5, 3.8, 4.2, 5.0, 3.9};
        double minimo = 4.0;
        int count = 0;
        for (double r : ratings) {
            if (r >= minimo) {
                count++;
            }
        }
        assertEquals("Hay 3 ratings >= 4.0", 3, count);
    }

    @Test
    public void testHighQualityRating() {
        rating = 4.5;
        boolean esAlta = rating >= 4.0;
        assertTrue("4.5 es calidad alta", esAlta);
    }

    @Test
    public void testLowQualityRating() {
        rating = 2.5;
        boolean esBaja = rating < 3.0;
        assertTrue("2.5 es calidad baja", esBaja);
    }

    @Test
    public void testRatingZero() {
        rating = 0.0;
        boolean isValid = rating >= 1.0;
        assertFalse("Rating 0 no es válido", isValid);
    }

    @Test
    public void testMaxRating() {
        rating = 5.0;
        boolean isMaximum = rating == 5.0;
        assertTrue("5.0 es máximo", isMaximum);
    }
}
