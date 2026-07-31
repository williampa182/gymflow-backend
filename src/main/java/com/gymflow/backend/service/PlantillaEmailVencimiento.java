package com.gymflow.backend.service;

import org.springframework.web.util.HtmlUtils;

public class PlantillaEmailVencimiento {

    /**
     * Generates an HTML email body using tables only and inline styles.
     * @param nombreUsuario recipient name
     * @param nombrePlan plan name
     * @param fechaVencimiento expiration date string
     * @return HTML email content
     */
    public static String generarHtml(String nombreUsuario, String nombrePlan, String fechaVencimiento) {
        String escUsuario = HtmlUtils.htmlEscape(nombreUsuario);
        String escPlan = HtmlUtils.htmlEscape(nombrePlan);
        String escFecha = HtmlUtils.htmlEscape(fechaVencimiento);
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='margin:0;padding:20px;background-color:#1c1d20;color:#f7f6f3;font-family:Arial,Helvetica,sans-serif;'>");
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' border='0' style='max-width:600px;margin:auto;background-color:#1c1d20;'>");
        sb.append("<tr>");
        sb.append("<td align='center' style='padding:20px;'>");
        sb.append("<h2 style='color:#f0b429;margin:0 0 20px;'>Renovación de Plan</h2>");
        sb.append("<p>Hola ").append(escUsuario).append(",</p>");
        sb.append("<p>Tu plan <strong>").append(escPlan).append("</strong> vence el ").append(escFecha).append(".</p>");
        sb.append("<p><a href='${CTA_URL}' style='display:inline-block;background-color:#f0b429;color:#1c1d20;padding:12px 24px;text-decoration:none;font-weight:bold;border-radius:4px;font-size:16px;'>Renovar ahora</a></p>");
        sb.append("<p>Para renovar, visita nuestro sitio web.</p>");
        sb.append("</td>");
        sb.append("</tr>");
        sb.append("</table>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Generates a plain text email body.
     * @param nombreUsuario recipient name
     * @param nombrePlan plan name
     * @param fechaVencimiento expiration date string
     * @return Plain text email content
     */
    public static String generarTextoPlano(String nombreUsuario, String nombrePlan, String fechaVencimiento) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hola ").append(nombreUsuario).append(".\n");
        sb.append("Tu plan ").append(nombrePlan).append(" vence el ").append(fechaVencimiento).append(".\n");
        sb.append("Renueva tu suscripción aquí: ${CTA_URL}\n");
        return sb.toString();
    }
}
