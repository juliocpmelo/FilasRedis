package com.example.demo.dto;

import com.example.demo.model.Tarefa;
import lombok.Data;

import java.io.Serializable;


@Data
public class TarefaScoredSetDto implements Serializable {
    Tarefa.Tipo tipo;
    Long usuarioId;

    public TarefaScoredSetDto(Tarefa.Tipo tipo, Long usuarioId){
        this.tipo = tipo;
        this.usuarioId = usuarioId;
    }
}
