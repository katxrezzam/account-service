package com.bootcamp.accountservice.model;

/**
 * Copia local del tipo de cliente de customer-service. Se duplica intencionalmente (no se
 * comparte una libreria de modelos entre microservicios) para no acoplar el build de
 * account-service al de customer-service - cada uno es un bounded context independiente, con su
 * propio repo. Los nombres de constante deben coincidir con customer-service.CustomerType para
 * que Jackson deserialice bien la respuesta REST.
 */
public enum CustomerType {
    PERSONAL,
    BUSINESS
}
