/**
 * The one node-building helper.
 *
 * Every view creates nodes and sets `textContent`; none interpolates a
 * string into markup. That is not a style preference — a title comes from a
 * caption, a caption comes from a channel, and the shortest path from there
 * to a script tag is `innerHTML`.
 */

export function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
