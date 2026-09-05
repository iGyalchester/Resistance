package com.resistance.mvc.converter;

import com.resistance.mvc.dao.ContactRepository;
import com.resistance.shared.models.entity.Contact;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Lets form binding turn a posted contact id back into the Contact entity,
 * so the application form's dropdown can populate JobApplication.contact.
 * Spring Boot auto-registers Converter beans with web data binding.
 *
 * <p>A converter has no request context, so it cannot know who is asking and
 * deliberately does not try: it resolves the id and nothing more.
 * {@code JobApplicationServiceImpl.saveForOwner} is what refuses a contact
 * belonging to another account, which is the same place every other
 * cross-account check lives.
 */
@Component
public class StringToContactConverter implements Converter<String, Contact> {

    private final ContactRepository contactRepository;

    public StringToContactConverter(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Override
    public Contact convert(String source) {
        if (source == null || source.isBlank()) {
            return null; // the "-- no contact --" option
        }
        return contactRepository.findById(Integer.parseInt(source)).orElse(null);
    }
}
