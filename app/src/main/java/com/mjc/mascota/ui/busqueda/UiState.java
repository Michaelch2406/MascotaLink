
package com.mjc.mascota.ui.busqueda;

// Clase base para los estados de la UI. El constructor es privado para que solo las clases internas puedan heredar.
public abstract class UiState<T> {

    private UiState() {}

    public static final class Loading<T> extends UiState<T> {
        public Loading() {}
    }

    public static final class Success<T> extends UiState<T> {
        private final T data;

        public Success(T data) {
            this.data = data;
        }

        public T getData() {
            return data;
        }
    }

    public static final class Error<T> extends UiState<T> {
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class Empty<T> extends UiState<T> {
        public Empty() {}
    }
}
