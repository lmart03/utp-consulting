package com.utp.assistant.crm.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Inferencias deterministas a partir del header From, para cuando el correo no trae firma ni empresa.
 * Nunca inventa teléfonos ni empresas de proveedores gratuitos.
 */
public final class ContactInference {

    private static final Set<String> FREE_PROVIDERS = Set.of(
            "gmail", "googlemail", "hotmail", "outlook", "live", "msn", "yahoo", "icloud", "me", "proton", "protonmail", "aol");

    private ContactInference() {
    }

    /** "Ana Torres &lt;Ana@NovaTech.com&gt;" → "ana@novatech.com" */
    public static String senderEmail(String from) {
        if (from == null) {
            return null;
        }
        int start = from.lastIndexOf('<');
        int end = from.lastIndexOf('>');
        String address = start >= 0 && end > start ? from.substring(start + 1, end) : from;
        address = address.strip().toLowerCase(Locale.ROOT);
        return address.isEmpty() ? null : address;
    }

    /** Nombre visible del header From, sin comillas; null si solo trae la dirección. */
    public static String displayName(String from) {
        if (from == null) {
            return null;
        }
        int start = from.lastIndexOf('<');
        if (start <= 0) {
            return null;
        }
        String name = from.substring(0, start).replace("\"", "").strip();
        return name.isEmpty() || name.contains("@") ? null : name;
    }

    /** "ana.torres@x.com" → "Ana Torres"; "ventas@x.com" → "Ventas". */
    public static String nameFromAddress(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String local = email.substring(0, email.indexOf('@')).replaceAll("[0-9]+", " ");
        String name = Arrays.stream(local.split("[._\\-+\\s]+"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
        return name.isBlank() ? null : name;
    }

    /** "ana@andinalogistics.com.pe" → "Andinalogistics"; null para gmail/hotmail/etc. */
    public static String companyFromDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String root = email.substring(email.indexOf('@') + 1).split("\\.")[0].toLowerCase(Locale.ROOT);
        if (root.isBlank() || FREE_PROVIDERS.contains(root)) {
            return null;
        }
        return Character.toUpperCase(root.charAt(0)) + root.substring(1);
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }
}
