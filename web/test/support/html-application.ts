import { applicationEnvironment, descendants, PageNode } from "./browser-application";
import { Node, TrackElement, Video } from "./player-environment";

const VOID = new Set(["area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"]);

/** Parse the shipped document; controller lookups never manufacture missing nodes. */
export async function htmlApplicationEnvironment(html: string) {
  const env = applicationEnvironment();
  try {
    const root = new PageNode("#DOCUMENT");
    const stack: Node[] = [root];
    const createElement = (tag: string) => tag === "video" ? new Video() : tag === "track" ? new TrackElement() : new PageNode(tag.toUpperCase());
    const ids = new Set<string>();
    const parser = new HTMLRewriter().on("*", {
      element(element) {
        const node = element.tagName === "video" && element.getAttribute("id") === "video"
          ? env.video : createElement(element.tagName);
        for (const [key, value] of element.attributes) {
          node.setAttribute(key, value);
          if (key === "id") {
            if (ids.has(value)) throw new Error(`Duplicate HTML id: ${value}`);
            ids.add(value);
            node.id = value;
          } else if (key === "class") {
            node.className = value;
            for (const name of value.split(/\s+/)) node.classes.add(name);
          } else if (key === "hidden") node.hidden = true;
          else if (key === "value") node.value = value;
          else if (key.startsWith("data-")) node.dataset[key.slice(5)] = value;
        }
        stack.at(-1)!.append(node);
        if (!VOID.has(element.tagName)) {
          stack.push(node);
          element.onEndTag(() => { stack.pop(); });
        }
      },
      text(text) {
        const node = new PageNode("#TEXT");
        node.textContent = text.text;
        stack.at(-1)!.append(node);
      },
    });
    await parser.transform(new Response(html)).text();
    if (stack.length !== 1) throw new Error("HTML fixture has unclosed elements");
    const matches = (node: Node, selector: string) => selector.startsWith("#") ? node.id === selector.slice(1)
      : selector.startsWith(".") ? node.className.split(/\s+/).includes(selector.slice(1))
      : node.tagName === selector.toUpperCase();
    const selectAll = (selector: string) => {
      const parts = selector.trim().split(/\s+/);
      return descendants(root).filter((node) => {
        if (!matches(node, parts.at(-1)!)) return false;
        let parent = node.parent;
        for (let i = parts.length - 2; i >= 0; i--) {
          while (parent && !matches(parent, parts[i]!)) parent = parent.parent;
          if (!parent) return false;
          parent = parent.parent;
        }
        return true;
      });
    };
    const lookup = (id: string) => descendants(root).find((node) => node.id === id) ?? null;
    Object.assign(env.document, {
      getElementById: lookup,
      querySelector: (selector: string) => selectAll(selector)[0] ?? null,
      querySelectorAll: selectAll,
      createElement,
      body: selectAll("body")[0], head: selectAll("head")[0], documentElement: selectAll("html")[0],
    });
    return { ...env, root, node(id: string) {
      const node = lookup(id);
      if (!node) throw new Error(`Missing HTML element: #${id}`);
      return node;
    } };
  } catch (error) {
    env.restore();
    throw error;
  }
}
