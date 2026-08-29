package com.resistance.data.dao;

import com.resistance.shared.models.entity.Contact;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class ContactDAOImpl implements ContactDAO {

    // define field for entity manager
    private EntityManager entityManager;

    // inject entity manager using constructor injection
    @Autowired
    public ContactDAOImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // implement save method
    @Override
    @Transactional
    public void save(Contact theContact) {
        entityManager.persist(theContact);
    }

    @Override
    public Contact findById(Integer id) {
        return entityManager.find(Contact.class, id);
    }

    @Override
    public List<Contact> findAll() {
        // create query
        TypedQuery<Contact> theQuery = entityManager.createQuery("FROM Contact", Contact.class);

        // return query results
        return theQuery.getResultList();
    }

    @Override
    public List<Contact> findByLastName(String theLastName) {
        // create query
        TypedQuery<Contact> theQuery = entityManager.createQuery(
                                        "FROM Contact WHERE lastName=:theData", Contact.class);

        // set query parameters
        theQuery.setParameter("theData", theLastName);

        // return query results
        return theQuery.getResultList();
    }

    @Override
    @Transactional
    public void update(Contact theContact) {
        entityManager.merge(theContact);
    }

    @Override
    @Transactional
    public void delete(Integer id) {

        // retrieve the contact
        Contact theContact = entityManager.find(Contact.class, id);

        // delete the contact
        entityManager.remove(theContact);
    }

    @Override
    @Transactional
    public int deleteAll() {

        int numRowsDeleted = entityManager.createQuery("DELETE FROM Contact").executeUpdate();

        return numRowsDeleted;
    }
}










