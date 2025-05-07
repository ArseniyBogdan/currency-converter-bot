package ru.spbstu.hsai.admin.entities;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "admins")
public class AdminDBO {
    @Id
    private ObjectId id;
    private String adminName;
    private String adminSurname;
    private LocalDateTime created;
}