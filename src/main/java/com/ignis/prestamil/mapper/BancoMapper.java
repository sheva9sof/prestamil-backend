package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Banco;
import com.ignis.prestamil.response.BancoResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BancoMapper {

    BancoResponse toBancoResponse(Banco banco);
}
