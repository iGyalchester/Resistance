package com.resistance.intake.parser;

import com.resistance.intake.service.InboundEmail;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern-based extraction for the formulaic "we received your application"
 * emails. The user forwards the confirmation, so the interesting text lives
 * in a "Fwd:" subject and a quoted body whose embedded "From:" line names
 * the company's sender.
 */
@Component
public class HeuristicConfirmationEmailParser implements ConfirmationEmailParser {

    private static final int F = Pattern.CASE_INSENSITIVE;

    // phrases that carry both company and position
    private static final List<Pattern> COMPANY_AND_POSITION = List.of(
            // "your application for the Backend Engineer position at Acme Corp"
            Pattern.compile("application for (?:the )?(?<position>[^.,;:\n]{2,80}?) (?:position|role|opening) at (?<company>[^.,;:!\n]{2,80})", F),
            // "your application to Acme Corp for the Backend Engineer role"
            Pattern.compile("application (?:to|with) (?<company>[^.,;:\n]{2,80}?) for (?:the )?(?<position>[^.,;:\n]{2,80}?)(?: (?:position|role|opening)|[.,;\n]|$)", F),
            // "applying for the Backend Engineer role at Acme Corp"
            Pattern.compile("applying for (?:the )?(?<position>[^.,;:\n]{2,80}?) (?:position|role|opening) at (?<company>[^.,;:!\n]{2,80})", F),
            // "your application for Backend Engineer at Acme Corp"
            Pattern.compile("application for (?<position>[^.,;:\n]{2,80}?) at (?<company>[^.,;:!\n]{2,80})", F));

    private static final List<Pattern> COMPANY_ONLY = List.of(
            Pattern.compile("thank you for applying (?:to|at|with) (?<company>[^.,;:!\n]{2,80})", F),
            Pattern.compile("(?:we(?:'|\u2019)?ve|we have) received your application (?:to|at|with) (?<company>[^.,;:!\n]{2,80})", F),
            Pattern.compile("your application (?:to|at|with) (?<company>[^.,;:!\n]{2,80})", F),
            Pattern.compile("application received[\\s:\u2013\u2014-]+(?<company>[^.,;:\n]{2,80})", F),
            Pattern.compile("your interest in (?:joining )?(?<company>[^.,;:!\n]{2,80})", F));

    private static final List<Pattern> POSITION_ONLY = List.of(
            Pattern.compile("for (?:the )?(?<position>[^.,;:\n]{2,80}?) (?:position|role|opening)", F),
            Pattern.compile("(?:position|role):\\s*(?<position>[^.,;\n]{2,80})", F));

    // "From: Acme Careers <no-reply@acme.com>" line inside the forwarded body
    private static final Pattern FORWARDED_FROM = Pattern.compile(
            "^\\s*>?\\s*From:\\s*\"?(?<name>[^\"<\\n]*?)\"?\\s*<?(?<address>[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,})>?\\s*$",
            Pattern.MULTILINE);

    // personal mail providers - their domain never names the company
    private static final Set<String> FREEMAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "outlook.com", "hotmail.com",
            "live.com", "icloud.com", "me.com", "aol.com", "proton.me", "protonmail.com", "gmx.com");

    // applicant-tracking systems - their domain names the ATS, not the company
    private static final Set<String> ATS_DOMAINS = Set.of(
            "greenhouse.io", "lever.co", "myworkday.com", "workday.com", "smartrecruiters.com",
            "ashbyhq.com", "icims.com", "jobvite.com", "bamboohr.com", "recruitee.com", "workablemail.com");

    @Override
    public Optional<ParsedApplication> parse(InboundEmail email) {
        String subject = stripForwardPrefixes(nullToEmpty(email.subject()));
        String body = unquote(nullToEmpty(email.body()));
        String text = subject + "\n" + body;

        String company = null;
        String position = null;

        for (Pattern pattern : COMPANY_AND_POSITION) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                company = clean(m.group("company"));
                position = clean(m.group("position"));
                break;
            }
        }

        if (company == null) {
            for (Pattern pattern : COMPANY_ONLY) {
                Matcher m = pattern.matcher(text);
                if (m.find()) {
                    company = clean(m.group("company"));
                    break;
                }
            }
        }

        if (position == null) {
            for (Pattern pattern : POSITION_ONLY) {
                Matcher m = pattern.matcher(text);
                if (m.find()) {
                    position = clean(m.group("position"));
                    break;
                }
            }
        }

        if (company == null) {
            company = companyFromForwardedSender(body);
        }

        if (company == null || company.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedApplication(company, position));
    }

    private String companyFromForwardedSender(String body) {
        Matcher m = FORWARDED_FROM.matcher(body);
        if (!m.find()) {
            return null;
        }

        String domain = m.group("address").substring(m.group("address").indexOf('@') + 1)
                .toLowerCase(Locale.ROOT);
        String name = clean(m.group("name"));

        boolean domainIsGeneric = FREEMAIL_DOMAINS.contains(domain)
                || ATS_DOMAINS.stream().anyMatch(ats -> domain.equals(ats) || domain.endsWith("." + ats));

        if (name != null && !name.isBlank() && !name.contains("@")) {
            // "Acme Corp Recruiting" -> "Acme Corp"
            String trimmed = name.replaceAll("(?i)\\s+(recruiting|careers|talent|hiring|team|jobs|no-?reply)$", "").trim();
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }

        if (domainIsGeneric) {
            return null;
        }

        // "jobs.acme.com" -> "Acme"
        String[] labels = domain.split("\\.");
        if (labels.length < 2) {
            return null;
        }
        String label = labels[labels.length - 2];
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    private String stripForwardPrefixes(String subject) {
        String result = subject.trim();
        while (true) {
            String stripped = result.replaceFirst("(?i)^(fwd?|re|fw)\\s*:\\s*", "");
            if (stripped.equals(result)) {
                return result;
            }
            result = stripped;
        }
    }

    private String unquote(String body) {
        return body.replaceAll("(?m)^\\s*>+\\s?", "");
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ")
                .replaceAll("^[\\s\"'*]+|[\\s\"'*!.]+$", "")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
