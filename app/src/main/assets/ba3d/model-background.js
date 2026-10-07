// Native code composites alpha before supplying an opaque CSS RGB color; reject other page/style inputs.
export function modelBackgroundColor(value) {
  return typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value) ? value.toLowerCase() : null;
}
