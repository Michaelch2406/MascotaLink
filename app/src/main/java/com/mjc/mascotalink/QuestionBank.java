package com.mjc.mascotalink;

public class QuestionBank {

    public static class Question {
        private final String text;
        private final String[] options;
        private final int correctAnswer;
        private final String explanation;
        private final int weight; // 1 normal, 2 crítico
        private final String category;

        public Question(String text, String[] options, int correctAnswer,
                        String explanation, int weight, String category) {
            this.text = text;
            this.options = options;
            this.correctAnswer = correctAnswer;
            this.explanation = explanation;
            this.weight = weight;
            this.category = category;
        }

        public String getText() { return text; }
        public String[] getOptions() { return options; }
        public int getCorrectAnswer() { return correctAnswer; }
        public String getExplanation() { return explanation; }
        public int getWeight() { return weight; }
        public String getCategory() { return category; }
    }

    public static final Question[] QUESTIONS = new Question[]{
            // COMPORTAMIENTO CANINO (3 preguntas - 1 punto c/u)
            new Question(
                    "Cuando un perro camina con la cola muy baja y orejas hacia atrás, ¿qué indica esto con mayor probabilidad?",
                    new String[]{"Está cómodo y jugando", "Miedo o estrés", "Está hambriento", "Está listo para saltar"},
                    1,
                    "Cola baja + orejas atrás suelen indicar miedo/ansiedad; observa el conjunto de señales.",
                    1,
                    "comportamiento"
            ),
            new Question(
                    "Si un perro hace la 'postura de juego' (patas delanteras abajo y trasero arriba) eso significa:",
                    new String[]{"Está a punto de atacar", "Está invitando a jugar", "Está temeroso", "Está enfermo"},
                    1,
                    "La 'bow' suele ser invitación a jugar, contexto importa.",
                    1,
                    "comportamiento"
            ),
            new Question(
                    "Si un perro muestra encías expuestas, labios tensos y rigidez corporal, la mejor acción es:",
                    new String[]{"Jugar con él para calmarlo", "Acercarse por detrás y agarrar la correa",
                            "Mantener distancia, no hacer contacto directo y calmar con voz baja", "Darle comida para distraerlo"},
                    2,
                    "La rigidez y labios tensos son señales de tensión; no introducirse ni forzar interacción.",
                    1,
                    "comportamiento"
            ),

            // PRIMEROS AUXILIOS (3 preguntas - 2 puntos c/u - CRÍTICAS)
            new Question(
                    "Si un perro tiene una herida que sangra activamente, la primera medida correcta es:",
                    new String[]{"Aplicar presión directa con una gasa/paño limpio", "Lavar la herida con alcohol inmediato",
                            "Esperar a ver si se detiene solo", "Darle agua para calmarlo"},
                    0,
                    "Controlar sangrado con presión directa es la medida inicial; luego bandaje y llevar al veterinario.",
                    2,
                    "primeros_auxilios"
            ),
            new Question(
                    "Un perro que presenta signos de golpe de calor (jadeo intenso, temperatura > 41°C, colapso). ¿Qué debes hacer de inmediato?",
                    new String[]{"Nada, esperar a que refresque", "Enfriar al perro con agua fresca en cabeza/axilas, ventilar y llevar urgentemente al veterinario",
                            "Darle antipirético humano (paracetamol)", "Darle agua caliente para hidratar"},
                    1,
                    "Enfriar y transporte urgente; no usar medicamentos humanos sin vet.",
                    2,
                    "primeros_auxilios"
            ),
            new Question(
                    "Si un perro está atragantado y no puede respirar, una maniobra a realizar (solo si es seguro) es:",
                    new String[]{"Introducir los dedos a la garganta y empujar el objeto",
                            "Realizar hasta 5 empujes abdominales (Heimlich) y revisar la boca, con cuidado de no empujar el objeto hacia más adentro",
                            "Dejar que se recupere solo", "Poner al perro de espaldas y sacudirlo"},
                    1,
                    "En perros se pueden usar compresiones/abdominal thrusts y barrido de boca con cuidado; deriva al vet.",
                    2,
                    "primeros_auxilios"
            ),

            // MANEJO DE EMERGENCIAS (3 preguntas - 2 puntos c/u - CRÍTICAS)
            new Question(
                    "Si dos perros empiezan a pelear, la mejor acción inmediata es:",
                    new String[]{"Meter la mano entre ellos para separarlos",
                            "Mantener la calma, intentar distraer con ruido fuerte o agua, y tratar de alejar a tu perro con correa sin ponerte entre ellos",
                            "Tirar de la correa con fuerza hacia atrás sin más", "Soltar a tu perro para que escape"},
                    1,
                    "No introducirte entre perros; distracción y control con correa; si no es posible, buscar ayuda.",
                    2,
                    "emergencias"
            ),
            new Question(
                    "Si sospechas intoxicación (ingesta de una sustancia tóxica), ¿qué haces primero?",
                    new String[]{"Inducir vómito siempre",
                            "Llamar inmediatamente al centro veterinario o a un servicio antitoxinas y seguir sus instrucciones; no inducir vómito sin indicación",
                            "Darle leche para diluir", "Esperar a que pasen las horas"},
                    1,
                    "La respuesta profesional es prioritaria; inducir vómito puede ser peligroso dependiendo de la sustancia.",
                    2,
                    "emergencias"
            ),
            new Question(
                    "Si un perro se escapa y está suelto en la calle, lo mejor es:",
                    new String[]{"Perseguirlo corriendo a toda velocidad",
                            "Mantener la calma, llamar con voz tranquila, usar comida o correa larga si está disponible, y evitar movimientos bruscos que asusten",
                            "Gritar y agitar las manos", "Intentar atraparlo con las manos inmediatamente"},
                    1,
                    "Perseguir puede aumentar que corra; estrategias calmadas aumentan la probabilidad de captura.",
                    2,
                    "emergencias"
            ),

            // NORMAS DE SEGURIDAD (3 preguntas - 1 punto c/u)
            new Question(
                    "¿Qué tipo de correa recomiendas para paseos en ciudad con tráfico?",
                    new String[]{"Correa retráctil larga", "Correa de 4–6 pies (1.2–1.8 m) y arnés o collar bien ajustado",
                            "Solo collar suelto sin correa", "Cuerda improvisada"},
                    1,
                    "Correa de longitud controlada y arnés ofrece mejor manejo y seguridad en tráfico.",
                    1,
                    "seguridad"
            ),
            new Question(
                    "Al acercarse a una persona desconocida con perro, la acción correcta es:",
                    new String[]{"Dejar que la persona acaricie inmediatamente",
                            "Preguntar permiso y observar señales del perro antes de permitir interacción",
                            "Evitar cualquier contacto", "Alejar inmediatamente al perro"},
                    1,
                    "Siempre pedir permiso y evaluar la reacción del perro antes de permitir interacciones.",
                    1,
                    "seguridad"
            ),
            new Question(
                    "¿Cuál es la práctica recomendada con correa en situaciones con bicicletas y tráfico?",
                    new String[]{"Mantener al perro junto al lado del paseador, correa corta, y cruzar en lugares seguros",
                            "Dejar correa larga y correr junto a la bicicleta",
                            "Soltar la correa para que esquive solo", "Que el perro camine por la mitad de la calle"},
                    0,
                    "Control es clave cerca de bicicletas/tráfico.",
                    1,
                    "seguridad"
            )
    };
}
