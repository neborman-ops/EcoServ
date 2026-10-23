package com.ecoserv.ui;

/**
 * Ошибка ввода в окне: обработчик бросает её, а игрок видит текст красным уведомлением в окне
 * (вместо сообщения в чат).
 */
public class UiError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UiError(String message) {
        super(message, null, false, false);
    }
}
