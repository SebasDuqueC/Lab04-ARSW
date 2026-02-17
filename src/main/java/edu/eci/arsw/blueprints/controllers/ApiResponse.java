package edu.eci.arsw.blueprints.controllers;

/**
 * Respuesta uniforme para la API: código, mensaje y dato opcional.
 */
public record ApiResponse<T>(int code, String message, T data) { }
