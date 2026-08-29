package com.resistance.intake.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMultipart;

/**
 * Pulls the plain-text content out of a (possibly multipart) MIME message.
 * Prefers text/plain parts; falls back to text/html with tags stripped.
 */
public final class MimeText {

    private MimeText() {
    }

    public static String extract(Part part) throws Exception {
        String plain = find(part, "text/plain");
        if (plain != null) {
            return plain;
        }
        String html = find(part, "text/html");
        if (html != null) {
            return html.replaceAll("(?s)<style.*?</style>", " ")
                    .replaceAll("(?s)<script.*?</script>", " ")
                    .replaceAll("<br\\s*/?>", "\n")
                    .replaceAll("</p>", "\n")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&amp;", "&");
        }
        return "";
    }

    private static String find(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType) && part.getContent() instanceof String text) {
            return text;
        }
        if (part.getContent() instanceof MimeMultipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String found = find(bodyPart, mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
