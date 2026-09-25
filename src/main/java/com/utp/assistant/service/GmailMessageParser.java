package com.utp.assistant.service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.utp.assistant.dto.GmailMessageDto;
import com.utp.assistant.exception.GmailApiException;
import com.utp.assistant.exception.MimeDecodingException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;

/**
 * Convierte un Message de Gmail API (format=full) en GmailMessageDto, extrayendo el cuerpo MIME:
 * prioriza text/plain (búsqueda recursiva en partes anidadas) y usa text/html convertido a texto como fallback.
 */
@Component
public class GmailMessageParser {

    private static final String TEXT_PLAIN = "text/plain";
    private static final String TEXT_HTML = "text/html";
    private static final Pattern CHARSET = Pattern.compile("charset=\"?([^\";\\s]+)", Pattern.CASE_INSENSITIVE);

    public GmailMessageDto toDto(Message message) {
        if (message == null || message.getId() == null) {
            throw new GmailApiException("Gmail devolvió un mensaje inválido.");
        }
        MessagePart payload = message.getPayload();
        return new GmailMessageDto(
                message.getId(),
                message.getThreadId(),
                header(payload, "From"),
                header(payload, "Subject"),
                header(payload, "Date"),
                message.getSnippet() == null ? "" : Parser.unescapeEntities(message.getSnippet(), false),
                extractBody(payload));
    }

    public String extractBody(MessagePart payload) {
        if (payload == null) {
            return "";
        }
        String plain = findPart(payload, TEXT_PLAIN);
        if (plain != null) {
            return plain.strip();
        }
        String html = findPart(payload, TEXT_HTML);
        return html != null ? htmlToText(html) : "";
    }

    /** Búsqueda en profundidad de la primera parte inline con el MIME type indicado. */
    private String findPart(MessagePart part, String mimeType) {
        if (isInlinePart(part, mimeType)) {
            return decodeBase64Url(part.getBody().getData(), charsetOf(part));
        }
        List<MessagePart> children = part.getParts();
        if (children != null) {
            for (MessagePart child : children) {
                String content = findPart(child, mimeType);
                if (content != null) {
                    return content;
                }
            }
        }
        return null;
    }

    private static boolean isInlinePart(MessagePart part, String mimeType) {
        boolean sameType = part.getMimeType() != null
                && part.getMimeType().toLowerCase(Locale.ROOT).startsWith(mimeType);
        boolean isAttachment = part.getFilename() != null && !part.getFilename().isBlank();
        return sameType && !isAttachment && part.getBody() != null && part.getBody().getData() != null;
    }

    static String decodeBase64Url(String data, Charset charset) {
        try {
            // Gmail usa Base64 URL-safe, a veces sin padding; se normaliza por robustez.
            String normalized = data.replaceAll("\\s", "").replace('+', '-').replace('/', '_');
            return new String(Base64.getUrlDecoder().decode(normalized), charset);
        } catch (IllegalArgumentException ex) {
            throw new MimeDecodingException("El contenido del mensaje no es Base64 URL-safe válido.", ex);
        }
    }

    private static Charset charsetOf(MessagePart part) {
        Matcher matcher = CHARSET.matcher(header(part, "Content-Type"));
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (IllegalArgumentException ignored) {
                // charset desconocido o no soportado: se usa UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    static String htmlToText(String html) {
        Element body = Jsoup.parse(html).body();
        body.select("script, style").remove();
        StringBuilder text = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    text.append(textNode.text());
                } else if (node instanceof Element element && element.normalName().equals("br")) {
                    text.append('\n');
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && element.isBlock() && !text.isEmpty()) {
                    text.append('\n');
                }
            }
        }, body);
        return text.toString()
                .replaceAll("[ \\t\\u00A0]+\\n", "\n")
                .replaceAll("\\n[ \\t\\u00A0]+", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static String header(MessagePart part, String name) {
        if (part == null || part.getHeaders() == null) {
            return "";
        }
        return part.getHeaders().stream()
                .filter(h -> name.equalsIgnoreCase(h.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .orElse("");
    }
}
