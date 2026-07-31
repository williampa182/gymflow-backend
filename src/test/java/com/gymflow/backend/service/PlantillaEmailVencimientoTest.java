package com.gymflow.backend.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.HtmlUtils;

public class PlantillaEmailVencimientoTest {

    @Test
    public void testGenerarHtmlEscaping() {
        String script = "<script>alert('xss')</script>";
        String html = PlantillaEmailVencimiento.generarHtml(script, "Plan &", "2026-08-01");
        
        String escapedScript = HtmlUtils.htmlEscape(script);
        assertTrue(html.contains(escapedScript), "Should escape user inputs");
        assertFalse(html.contains("<script>"), "Should not contain raw script tag");
        assertTrue(html.contains("Plan &amp;"), "Should escape ampersands");
    }

    @Test
    public void testGenerarHtmlStructure() {
        String html = PlantillaEmailVencimiento.generarHtml("User", "Plan", "2026-08-01");
        assertTrue(html.contains("<table"), "Should use table layout");
        assertFalse(html.contains("display:flex"), "Should not use flexbox");
        assertFalse(html.contains("display:grid"), "Should not use grid");
    }

    @Test
    public void testGenerarTextoPlano() {
        String text = PlantillaEmailVencimiento.generarTextoPlano("User", "Plan", "2026-08-01");
        assertTrue(text.contains("${CTA_URL}"), "Plain text should include CTA URL");
    }
}
