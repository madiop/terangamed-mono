package com.terangamed.common.pagination;

import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Représentation API stable d'une page de résultats.
 *
 * <p>On évite d'exposer directement {@code org.springframework.data.domain.Page} via REST
 * car son format JSON n'est pas garanti stable entre versions de Spring Data,
 * et il expose des détails internes ({@code pageable}, {@code sort}) inutiles au client.
 *
 * <p>Utilisation typique dans un contrôleur :
 * <pre>
 *   Page&lt;PatientEntity&gt; page = patientRepository.findAll(spec, pageable);
 *   PageResponse&lt;PatientDto&gt; response = PageResponse.from(page, patientMapper::toDto);
 * </pre>
 */
@Builder
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    public static <T, U> PageResponse<U> from(Page<T> page, Function<T, U> mapper) {
        return PageResponse.<U>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
