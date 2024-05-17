package com.example.demo.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@Data
public class Tarefa {
    public enum Tipo{
        INSERT, DELETE, UPDATE
    }
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Tipo tipo;

    String payload; //json com a operação
}
