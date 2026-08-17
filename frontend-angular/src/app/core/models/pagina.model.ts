// Page<T> de Spring Data tal como la serializa Jackson
// (org.springframework.data.domain.Page). Campos que el frontend consume:
// content + totalPages + los demas que documentan la paginacion real.
export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  numberOfElements: number;
  empty: boolean;
}