package com.resistance.data.dao;

import com.resistance.shared.models.entity.Contact;

import java.util.List;

public interface ContactDAO {

    void save(Contact theContact);

    Contact findById(Integer id);

    List<Contact> findAll();

    List<Contact> findByLastName(String theLastName);

    void update(Contact theContact);

    void delete(Integer id);

    int deleteAll();
}
