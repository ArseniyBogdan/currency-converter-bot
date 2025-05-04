package ru.spbstu.hsai.admin.dao;

import org.bson.types.ObjectId;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import ru.spbstu.hsai.admin.entities.AdminDBO;

@Repository
public interface AdminDAO extends ReactiveCrudRepository<AdminDBO, ObjectId> {
}
