package ru.spbstu.hsai.user.dao;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import ru.spbstu.hsai.user.entities.UserDBO;

@Repository
public interface UserDAO extends ReactiveCrudRepository<UserDBO, String> {
}
