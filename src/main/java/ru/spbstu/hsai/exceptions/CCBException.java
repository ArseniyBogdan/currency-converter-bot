package ru.spbstu.hsai.exceptions;

public class CCBException extends RuntimeException {
    public CCBException(String message, Throwable e){
        super(message, e);
    }
}
