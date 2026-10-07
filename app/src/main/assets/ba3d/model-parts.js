// Asset-authored visibility rules are data; only existing model nodes can be affected.
export function createModelParts(root) {
  const entries = [];
  root.traverse(node => entries.push({node, baseline:node.visible && !node.userData?.optional}));
  function names(value) {
    return (Array.isArray(value) ? value : [value]).filter(name => typeof name === 'string' && name.length > 0);
  }
  function matches(node, name) {
    return node.name === name || node.userData?.name === name ||
      (Object.hasOwn(node.userData || {}, name) && node.userData[name] === true);
  }
  return {
    apply(rule = {}) {
      const hide = names(rule.hide), show = names(rule.show);
      for (const {node, baseline} of entries) {
        node.visible = show.some(name => matches(node, name)) ||
          (baseline && !hide.some(name => matches(node, name)));
      }
    },
    visibleTagged() {
      return entries.filter(({node}) => node.visible && Object.keys(node.userData || {}).some(key =>
        key === 'optional' || /^(prop|switch)\d+$/.test(key))).map(({node}) => node.name);
    },
  };
}
