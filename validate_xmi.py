#!/usr/bin/env python3
"""
Validate XMI files structurally against what Enterprise Architect expects.

Checks:
 1. Well-formed XML
 2. Required namespaces (XMI 2.1, UML 2.1) and xmi:version="2.1"
 3. Root element is xmi:XMI
 4. uml:Model element with xmi:type, xmi:id, name
 5. Every packagedElement has xmi:type, xmi:id, name
 6. Every ownedAttribute has xmi:type="uml:Property", xmi:id, name, visibility
 7. Every ownedAttribute has a child <type> with xmi:idref
 8. Every ownedAttribute has lowerValue and upperValue children with xmi:type="uml:LiteralInteger"
 9. Every ownedLiteral has xmi:type="uml:EnumerationLiteral", xmi:id, name
10. Every generalization has xmi:type="uml:Generalization", xmi:id, general
11. Every uml:Association has memberEnd and ownedEnd children with <type> children
12. ID uniqueness: all xmi:id values are unique
13. ID referential integrity: every xmi:idref and general references an existing xmi:id
    (cross-file references produce a warning, not a failure)
"""

import sys
import xml.etree.ElementTree as ET
from collections import Counter

NS_XMI = "http://schema.omg.org/spec/XMI/2.1"
NS_UML = "http://schema.omg.org/spec/UML/2.1"

XMI_TYPE = f"{{{NS_XMI}}}type"
XMI_ID = f"{{{NS_XMI}}}id"
XMI_IDREF = f"{{{NS_XMI}}}idref"
XMI_VERSION = f"{{{NS_XMI}}}version"

# Qualified tag names
TAG_XMI_XMI = f"{{{NS_XMI}}}XMI"
TAG_UML_MODEL = f"{{{NS_UML}}}Model"


class ValidationResult:
    def __init__(self):
        self.results = []  # list of (status, check_num, description, details)

    def pass_(self, check_num, description, details=""):
        self.results.append(("PASS", check_num, description, details))

    def fail(self, check_num, description, details=""):
        self.results.append(("FAIL", check_num, description, details))

    def warn(self, check_num, description, details=""):
        self.results.append(("WARN", check_num, description, details))

    def has_failures(self):
        return any(r[0] == "FAIL" for r in self.results)

    def print_report(self):
        for status, check_num, description, details in self.results:
            line = f"  [{status}] Check {check_num:2d}: {description}"
            if details:
                line += f"\n             {details}"
            print(line)


def validate_file(filepath):
    """Validate a single XMI file. Returns a ValidationResult."""
    vr = ValidationResult()

    # ------------------------------------------------------------------
    # Check 1: Well-formed XML
    # ------------------------------------------------------------------
    try:
        tree = ET.parse(filepath)
        root = tree.getroot()
        vr.pass_(1, "Well-formed XML")
    except ET.ParseError as e:
        vr.fail(1, "Well-formed XML", str(e))
        return vr  # Cannot continue without a parsed tree

    # ------------------------------------------------------------------
    # Check 2: Required namespaces and xmi:version
    # ------------------------------------------------------------------
    # ElementTree rewrites namespace-prefixed tags into Clark notation.
    # We check the root tag and attributes to verify the correct namespaces.
    ns_ok = True
    details_parts = []

    if not root.tag.startswith(f"{{{NS_XMI}}}"):
        ns_ok = False
        details_parts.append(f"Root tag does not use XMI 2.1 namespace (got {root.tag})")

    # Check for uml namespace by looking for any element using it
    has_uml_ns = False
    for elem in root.iter():
        if elem.tag.startswith(f"{{{NS_UML}}}"):
            has_uml_ns = True
            break
        if XMI_TYPE in elem.attrib and elem.attrib[XMI_TYPE].startswith("uml:"):
            has_uml_ns = True
            break
    if not has_uml_ns:
        ns_ok = False
        details_parts.append("No UML 2.1 namespace usage found")

    version = root.attrib.get(XMI_VERSION, "")
    if version != "2.1":
        ns_ok = False
        details_parts.append(f"xmi:version is '{version}', expected '2.1'")

    if ns_ok:
        vr.pass_(2, "Required namespaces (XMI 2.1, UML 2.1) and xmi:version='2.1'")
    else:
        vr.fail(2, "Required namespaces and xmi:version", "; ".join(details_parts))

    # ------------------------------------------------------------------
    # Check 3: Root element is xmi:XMI
    # ------------------------------------------------------------------
    if root.tag == TAG_XMI_XMI:
        vr.pass_(3, "Root element is xmi:XMI")
    else:
        vr.fail(3, "Root element is xmi:XMI", f"Got {root.tag}")

    # ------------------------------------------------------------------
    # Check 4: uml:Model with xmi:type, xmi:id, name
    # ------------------------------------------------------------------
    models = root.findall(TAG_UML_MODEL)
    if not models:
        vr.fail(4, "uml:Model element present", "No uml:Model found")
    else:
        model = models[0]
        missing = []
        if model.attrib.get(XMI_TYPE) != "uml:Model":
            missing.append('xmi:type="uml:Model"')
        if XMI_ID not in model.attrib:
            missing.append("xmi:id")
        if "name" not in model.attrib:
            missing.append("name")
        if missing:
            vr.fail(4, "uml:Model attributes", f"Missing: {', '.join(missing)}")
        else:
            vr.pass_(4, "uml:Model with xmi:type, xmi:id, name")

    # ------------------------------------------------------------------
    # Collect all xmi:id values for checks 12 and 13
    # ------------------------------------------------------------------
    all_ids = []
    all_idrefs = []   # (idref_value, element_tag, context)
    all_generals = [] # (general_value, element_tag, context)

    for elem in root.iter():
        xid = elem.attrib.get(XMI_ID)
        if xid is not None:
            all_ids.append(xid)
        xidref = elem.attrib.get(XMI_IDREF)
        if xidref is not None:
            all_idrefs.append((xidref, elem.tag, elem.attrib.get("name", "")))
        gen = elem.attrib.get("general")
        if gen is not None:
            all_generals.append((gen, elem.tag, elem.attrib.get(XMI_ID, "")))

    id_set = set(all_ids)

    # ------------------------------------------------------------------
    # Check 5: Every packagedElement has xmi:type, xmi:id, name
    # ------------------------------------------------------------------
    pkg_elements = list(root.iter("packagedElement"))
    pkg_errors = []
    for pe in pkg_elements:
        missing = []
        if XMI_TYPE not in pe.attrib:
            missing.append("xmi:type")
        if XMI_ID not in pe.attrib:
            missing.append("xmi:id")
        if "name" not in pe.attrib:
            missing.append("name")
        if missing:
            eid = pe.attrib.get(XMI_ID, pe.attrib.get("name", "<unknown>"))
            pkg_errors.append(f"  {eid}: missing {', '.join(missing)}")
    if not pkg_elements:
        vr.warn(5, "packagedElement attributes", "No packagedElement elements found")
    elif pkg_errors:
        vr.fail(5, f"Every packagedElement has xmi:type, xmi:id, name ({len(pkg_errors)} violations)",
                "\n             ".join(pkg_errors[:10]) + (f"\n             ... and {len(pkg_errors)-10} more" if len(pkg_errors) > 10 else ""))
    else:
        vr.pass_(5, f"Every packagedElement has xmi:type, xmi:id, name ({len(pkg_elements)} checked)")

    # ------------------------------------------------------------------
    # Check 6: Every ownedAttribute has xmi:type="uml:Property", xmi:id, name, visibility
    # ------------------------------------------------------------------
    owned_attrs = list(root.iter("ownedAttribute"))
    attr_errors = []
    for oa in owned_attrs:
        missing = []
        if oa.attrib.get(XMI_TYPE) != "uml:Property":
            missing.append('xmi:type="uml:Property"')
        if XMI_ID not in oa.attrib:
            missing.append("xmi:id")
        if "name" not in oa.attrib:
            missing.append("name")
        if "visibility" not in oa.attrib:
            missing.append("visibility")
        if missing:
            eid = oa.attrib.get(XMI_ID, oa.attrib.get("name", "<unknown>"))
            attr_errors.append(f"  {eid}: missing {', '.join(missing)}")
    if not owned_attrs:
        vr.warn(6, "ownedAttribute attributes", "No ownedAttribute elements found")
    elif attr_errors:
        vr.fail(6, f"Every ownedAttribute has required attrs ({len(attr_errors)} violations)",
                "\n             ".join(attr_errors[:10]) + (f"\n             ... and {len(attr_errors)-10} more" if len(attr_errors) > 10 else ""))
    else:
        vr.pass_(6, f"Every ownedAttribute has xmi:type, xmi:id, name, visibility ({len(owned_attrs)} checked)")

    # ------------------------------------------------------------------
    # Check 7: Every ownedAttribute has a <type> child with xmi:idref
    # ------------------------------------------------------------------
    type_errors = []
    for oa in owned_attrs:
        type_children = oa.findall("type")
        if not type_children:
            eid = oa.attrib.get(XMI_ID, oa.attrib.get("name", "<unknown>"))
            type_errors.append(f"  {eid}: no <type> child")
        else:
            for tc in type_children:
                if XMI_IDREF not in tc.attrib:
                    eid = oa.attrib.get(XMI_ID, oa.attrib.get("name", "<unknown>"))
                    type_errors.append(f"  {eid}: <type> child missing xmi:idref")
    if not owned_attrs:
        vr.warn(7, "ownedAttribute <type> children", "No ownedAttribute elements found")
    elif type_errors:
        vr.fail(7, f"Every ownedAttribute has <type> with xmi:idref ({len(type_errors)} violations)",
                "\n             ".join(type_errors[:10]) + (f"\n             ... and {len(type_errors)-10} more" if len(type_errors) > 10 else ""))
    else:
        vr.pass_(7, f"Every ownedAttribute has <type> child with xmi:idref ({len(owned_attrs)} checked)")

    # ------------------------------------------------------------------
    # Check 8: Every ownedAttribute has lowerValue and upperValue children
    #          with xmi:type="uml:LiteralInteger"
    # ------------------------------------------------------------------
    bound_errors = []
    for oa in owned_attrs:
        eid = oa.attrib.get(XMI_ID, oa.attrib.get("name", "<unknown>"))
        lower = oa.findall("lowerValue")
        upper = oa.findall("upperValue")
        if not lower:
            bound_errors.append(f"  {eid}: missing lowerValue")
        elif lower[0].attrib.get(XMI_TYPE) != "uml:LiteralInteger":
            bound_errors.append(f"  {eid}: lowerValue xmi:type is not 'uml:LiteralInteger'")
        if not upper:
            bound_errors.append(f"  {eid}: missing upperValue")
        elif upper[0].attrib.get(XMI_TYPE) != "uml:LiteralInteger":
            bound_errors.append(f"  {eid}: upperValue xmi:type is not 'uml:LiteralInteger'")
    if not owned_attrs:
        vr.warn(8, "ownedAttribute lowerValue/upperValue", "No ownedAttribute elements found")
    elif bound_errors:
        vr.fail(8, f"Every ownedAttribute has lowerValue/upperValue ({len(bound_errors)} violations)",
                "\n             ".join(bound_errors[:10]) + (f"\n             ... and {len(bound_errors)-10} more" if len(bound_errors) > 10 else ""))
    else:
        vr.pass_(8, f"Every ownedAttribute has lowerValue and upperValue ({len(owned_attrs)} checked)")

    # ------------------------------------------------------------------
    # Check 9: Every ownedLiteral has required attributes
    # ------------------------------------------------------------------
    owned_literals = list(root.iter("ownedLiteral"))
    lit_errors = []
    for ol in owned_literals:
        missing = []
        if ol.attrib.get(XMI_TYPE) != "uml:EnumerationLiteral":
            missing.append('xmi:type="uml:EnumerationLiteral"')
        if XMI_ID not in ol.attrib:
            missing.append("xmi:id")
        if "name" not in ol.attrib:
            missing.append("name")
        if missing:
            eid = ol.attrib.get(XMI_ID, ol.attrib.get("name", "<unknown>"))
            lit_errors.append(f"  {eid}: missing {', '.join(missing)}")
    if not owned_literals:
        vr.warn(9, "ownedLiteral attributes", "No ownedLiteral elements found")
    elif lit_errors:
        vr.fail(9, f"Every ownedLiteral has required attrs ({len(lit_errors)} violations)",
                "\n             ".join(lit_errors[:10]) + (f"\n             ... and {len(lit_errors)-10} more" if len(lit_errors) > 10 else ""))
    else:
        vr.pass_(9, f"Every ownedLiteral has xmi:type, xmi:id, name ({len(owned_literals)} checked)")

    # ------------------------------------------------------------------
    # Check 10: Every generalization has required attributes
    # ------------------------------------------------------------------
    generalizations = list(root.iter("generalization"))
    gen_errors = []
    for g in generalizations:
        missing = []
        if g.attrib.get(XMI_TYPE) != "uml:Generalization":
            missing.append('xmi:type="uml:Generalization"')
        if XMI_ID not in g.attrib:
            missing.append("xmi:id")
        if "general" not in g.attrib:
            missing.append("general")
        if missing:
            eid = g.attrib.get(XMI_ID, "<unknown>")
            gen_errors.append(f"  {eid}: missing {', '.join(missing)}")
    if not generalizations:
        vr.warn(10, "generalization attributes", "No generalization elements found")
    elif gen_errors:
        vr.fail(10, f"Every generalization has required attrs ({len(gen_errors)} violations)",
                "\n             ".join(gen_errors[:10]) + (f"\n             ... and {len(gen_errors)-10} more" if len(gen_errors) > 10 else ""))
    else:
        vr.pass_(10, f"Every generalization has xmi:type, xmi:id, general ({len(generalizations)} checked)")

    # ------------------------------------------------------------------
    # Check 11: Every uml:Association has memberEnd and ownedEnd with <type>
    # ------------------------------------------------------------------
    associations = [pe for pe in root.iter("packagedElement")
                    if pe.attrib.get(XMI_TYPE) == "uml:Association"]
    assoc_errors = []
    for a in associations:
        eid = a.attrib.get(XMI_ID, a.attrib.get("name", "<unknown>"))
        member_ends = a.findall("memberEnd")
        owned_ends = a.findall("ownedEnd")
        if not member_ends:
            assoc_errors.append(f"  {eid}: no memberEnd children")
        if not owned_ends:
            assoc_errors.append(f"  {eid}: no ownedEnd children")
        else:
            for oe in owned_ends:
                type_children = oe.findall("type")
                if not type_children:
                    oe_id = oe.attrib.get(XMI_ID, "<unknown>")
                    assoc_errors.append(f"  {eid}/{oe_id}: ownedEnd missing <type> child")
    if not associations:
        vr.warn(11, "uml:Association structure", "No uml:Association elements found")
    elif assoc_errors:
        vr.fail(11, f"Every uml:Association has memberEnd/ownedEnd with <type> ({len(assoc_errors)} violations)",
                "\n             ".join(assoc_errors[:10]) + (f"\n             ... and {len(assoc_errors)-10} more" if len(assoc_errors) > 10 else ""))
    else:
        vr.pass_(11, f"Every uml:Association has memberEnd and ownedEnd with <type> ({len(associations)} checked)")

    # ------------------------------------------------------------------
    # Check 12: ID uniqueness
    # ------------------------------------------------------------------
    id_counts = Counter(all_ids)
    duplicates = {k: v for k, v in id_counts.items() if v > 1}
    if duplicates:
        dup_details = [f"  '{k}' appears {v} times" for k, v in sorted(duplicates.items())[:15]]
        vr.fail(12, f"All xmi:id values are unique ({len(duplicates)} duplicates)",
                "\n             ".join(dup_details) + (f"\n             ... and {len(duplicates)-15} more" if len(duplicates) > 15 else ""))
    else:
        vr.pass_(12, f"All xmi:id values are unique ({len(all_ids)} IDs)")

    # ------------------------------------------------------------------
    # Check 13: ID referential integrity
    # ------------------------------------------------------------------
    broken_refs = []
    warn_refs = []
    for ref_val, tag, context in all_idrefs:
        if ref_val not in id_set:
            warn_refs.append(f"  xmi:idref='{ref_val}' (in <{tag.split('}')[-1] if '}' in tag else tag}>)")
    for ref_val, tag, context in all_generals:
        if ref_val not in id_set:
            warn_refs.append(f"  general='{ref_val}' (in <{tag.split('}')[-1] if '}' in tag else tag}> id={context})")

    if warn_refs:
        # These could be cross-file references, so warn instead of fail
        vr.warn(13, f"ID referential integrity ({len(warn_refs)} unresolved references -- may be cross-file)",
                "\n             ".join(warn_refs[:15]) + (f"\n             ... and {len(warn_refs)-15} more" if len(warn_refs) > 15 else ""))
    else:
        total_refs = len(all_idrefs) + len(all_generals)
        vr.pass_(13, f"ID referential integrity ({total_refs} references all resolve)")

    return vr


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <file.xmi> [file2.xmi ...]")
        sys.exit(1)

    overall_fail = False
    for filepath in sys.argv[1:]:
        print(f"\n{'='*72}")
        print(f"Validating: {filepath}")
        print(f"{'='*72}")
        vr = validate_file(filepath)
        vr.print_report()
        if vr.has_failures():
            overall_fail = True
            print(f"\n  ** RESULT: FAILED **")
        else:
            has_warnings = any(r[0] == "WARN" for r in vr.results)
            if has_warnings:
                print(f"\n  ** RESULT: PASSED (with warnings) **")
            else:
                print(f"\n  ** RESULT: PASSED **")

    print()
    if overall_fail:
        print("Overall: SOME FILES FAILED VALIDATION")
        sys.exit(1)
    else:
        print("Overall: ALL FILES PASSED VALIDATION")
        sys.exit(0)


if __name__ == "__main__":
    main()
