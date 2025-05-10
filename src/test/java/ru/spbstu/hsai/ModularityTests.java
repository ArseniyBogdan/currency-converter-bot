package ru.spbstu.hsai;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    @Test
    void verifyModularity() {
        ApplicationModules.of(Application.class).verify();
    }

    @Test
    void generateGraph(){
        ApplicationModules modules = ApplicationModules.of(Application.class);
        // Генерация графа
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}