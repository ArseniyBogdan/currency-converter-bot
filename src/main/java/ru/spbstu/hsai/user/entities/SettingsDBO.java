package ru.spbstu.hsai.user.entities;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "user_settings")
public class SettingsDBO {
    @Id
    private Long chatId;
    private String homeCurrencyCode;
    private ObjectId currencyPairId;
}
