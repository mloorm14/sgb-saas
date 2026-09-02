// Page<T> de Spring Data tal como la serializa Jackson.
// Spring Boot 4 puede anidar size/number/total* dentro de `page`.
export interface PageMeta {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  numberOfElements: number;
  empty: boolean;
  page?: PageMeta;
}

export function normalizarPagina<T>(data: Page<T>): Page<T> {
  const meta = data.page;
  return {
    ...data,
    content: data.content ?? [],
    totalPages: data.totalPages ?? meta?.totalPages ?? 0,
    totalElements: data.totalElements ?? meta?.totalElements ?? (data.content?.length ?? 0),
    size: data.size ?? meta?.size ?? 0,
    number: data.number ?? meta?.number ?? 0
  };
}