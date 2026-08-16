// Contrato de FavoritoController (/api/v1/favoritos), ver
// FavoritoResponseDTO en backend-springboot. El usuario SIEMPRE se
// resuelve del token en el backend: nunca se manda usuarioId.
// Gap real documentado: el DTO no trae tienePortada/portadaNombre, por lo
// que la tarjeta de favoritos no sabe si el libro tiene portada binaria.
export interface Favorito {
  usuarioId: number;
  libroId: number;
  tituloLibro: string;
  agregadoEn: string;
}