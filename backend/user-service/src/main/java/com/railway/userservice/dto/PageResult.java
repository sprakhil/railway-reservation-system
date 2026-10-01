package com.railway.userservice.dto;
import java.util.List; import org.springframework.data.domain.Page;
public record PageResult<T>(List<T> content,int page,int size,long totalElements,int totalPages) {
 public static <T> PageResult<T> of(Page<T> p){return new PageResult<>(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages());}
}
