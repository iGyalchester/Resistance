package com.resistance.data;

import com.resistance.data.dao.ContactDAO;
import com.resistance.shared.models.entity.Contact;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
@EntityScan("com.resistance.shared.models.entity")
public class DataServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DataServiceApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(ContactDAO contactDAO) {

		return runner -> {
			// createContact(contactDAO);

			createMultipleContacts(contactDAO);

			// readContact(contactDAO);

			// queryForContacts(contactDAO);

			// queryForContactsByLastName(contactDAO);

			// updateContact(contactDAO);

			// deleteContact(contactDAO);

			// deleteAllContacts(contactDAO);
		};
	}

	private void deleteAllContacts(ContactDAO contactDAO) {

		System.out.println("Deleting all contacts");
		int numRowsDeleted = contactDAO.deleteAll();
		System.out.println("Deleted row count: " + numRowsDeleted);
	}

	private void deleteContact(ContactDAO contactDAO) {

		int contactId = 3;
		System.out.println("Deleting contact id: " + contactId);
		contactDAO.delete(contactId);
	}

	private void updateContact(ContactDAO contactDAO) {

		// retrieve contact based on the id: primary key
		int contactId = 1;
		System.out.println("Getting contact with id: " + contactId);
		Contact myContact = contactDAO.findById(contactId);

		// change first name to "John"
		System.out.println("Updating contact ...");
		myContact.setFirstName("John");

		// update the contact
		contactDAO.update(myContact);

		// display the updated contact
		System.out.println("Updated contact: " + myContact);
	}

	private void queryForContactsByLastName(ContactDAO contactDAO) {

		// get a list of contacts
		List<Contact> theContacts = contactDAO.findByLastName("Doe");

		// display list of contacts
		for (Contact tempContact : theContacts) {
			System.out.println(tempContact);
		}
	}

	private void queryForContacts(ContactDAO contactDAO) {

		// get a list of contacts
		List<Contact> theContacts = contactDAO.findAll();

		// display list of contacts
		for (Contact tempContact : theContacts) {
			System.out.println(tempContact);
		}
	}

	private void readContact(ContactDAO contactDAO) {

		// create  a contact object
		System.out.println("Creating new contact object ...");
		Contact tempContact = new Contact("Daffy", "Duck", "daffy@resistance.com");

		// save the contact
		System.out.println("Saving the contact ...");
		contactDAO.save(tempContact);

		// display id of the saved contact
		int theId = tempContact.getId();
		System.out.println("Saved contact. Generated id: " + theId);

		// retrieve contact based on the id: primary key
		System.out.println("Retrieving contact with id: " + theId);
		Contact myContact = contactDAO.findById(theId);

		// display contact
		System.out.println("Found the contact: " + myContact);
	}

	private void createMultipleContacts(ContactDAO contactDAO) {

		// create multiple contacts
		System.out.println("Creating 3 contact objects ...");
		Contact tempContact1 = new Contact("John", "Doe", "john@resistance.com");
		Contact tempContact2 = new Contact("Mary", "Public", "mary@resistance.com");
		Contact tempContact3 = new Contact("Bonita", "Applebum", "bonita@resistance.com");

		// save the contact objects
		System.out.println("Saving the contacts ...");
		contactDAO.save(tempContact1);
		contactDAO.save(tempContact2);
		contactDAO.save(tempContact3);
	}

	private void createContact(ContactDAO contactDAO) {

		// create the contact object
		System.out.println("Creating new contact object ...");
		Contact tempContact = new Contact("Paul", "Doe", "paul@resistance.com");

		// save the contact object
		System.out.println("Saving the contact ...");
		contactDAO.save(tempContact);

		// display id of the saved contact
		System.out.println("Saved contact. Generated id: " + tempContact.getId());
	}
}







