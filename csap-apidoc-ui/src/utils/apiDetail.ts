/**
 * Whether the right-hand detail panel has an endpoint to show.
 *
 * Previously the panel rendered only when the endpoint declared request
 * headers (`headers.length > 0`), so endpoints without headers showed the
 * empty placeholder. Presence of a loaded detail (method / path / title) is
 * what matters; headers are just one optional section of it.
 */
export function hasApiDetail(
  detail: { method?: string | null; patch?: string | null; apiTitle?: string | null } | null | undefined,
): boolean {
  if (!detail) return false;
  return !!(detail.method || detail.patch || detail.apiTitle);
}

export const EMPTY_API_DETAIL = {
  apiTitle: '',
  paramsTitle: '',
  method: '',
  patch: '',
};
