package ru.spbstu.hsai.user.entities;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSettings {
    private String homeCurrency;
    private String defaultPair;
}
