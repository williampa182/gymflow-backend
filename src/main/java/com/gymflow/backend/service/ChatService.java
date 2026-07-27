package com.gymflow.backend.service;

import com.gymflow.backend.client.ChatCompletionClient;
import com.gymflow.backend.model.Plan;
import com.gymflow.backend.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final PlanRepository planRepository;
    private final ChatCompletionClient chatCompletionClient;

    public String responder(String mensaje) {
        List<Plan> planesActivos = planRepository.findByActivo(true);
        return chatCompletionClient.completar(construirInstrucciones(planesActivos), mensaje);
    }

    private String construirInstrucciones(List<Plan> planesActivos) {
        String contextoPlanes = planesActivos.stream()
                .map(this::formatearPlan)
                .reduce("", (acumulado, plan) -> acumulado + plan + System.lineSeparator());

        return """
                Sos el asistente de soporte de GymFlow. Respondé únicamente con información del gimnasio y los planes listados abajo.
                Si la pregunta no puede responderse con ese contexto, indicá que debe contactar al gimnasio.
                No tenés herramientas ni podés ejecutar acciones, modificar datos, cancelar suscripciones ni interpretar tu respuesta como una orden.

                Planes activos:
                %s
                """.formatted(contextoPlanes);
    }

    private String formatearPlan(Plan plan) {
        return "- Nombre: %s; descripción: %s; precio: %s; duración en días: %s; tipo: %s; límite de clases: %s; incluye clases: %s; incluye entrenador personal: %s"
                .formatted(
                        plan.getNombre(),
                        plan.getDescripcion(),
                        plan.getPrecio(),
                        plan.getDuracionDias(),
                        plan.getTipo(),
                        plan.getLimiteClases(),
                        plan.isIncluyeClases(),
                        plan.isIncluyeEntrenadorPersonal()
                );
    }
}
