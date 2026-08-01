package com.gymflow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableCaching removido (hallazgo 1.2 del THREAT_MODEL.md, hardening
// preventivo): no hay ningún @Cacheable activo en el proyecto desde que se
// sacó de PlanService al agregar paginación. Ver docs/THREAT_MODEL.md sección 7.5.
// @EnableScheduling habilita el job de aviso de vencimientos
// (NotificacionVencimientoScheduler), gateado por app.email.aviso-enabled.
@SpringBootApplication
@EnableScheduling
public class GymflowBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GymflowBackendApplication.class, args);
	}

}
