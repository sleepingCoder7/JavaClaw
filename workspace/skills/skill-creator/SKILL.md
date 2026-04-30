---
name: skill-creator
description: Create new skills, modify and improve existing skills for JavaClaw. Use when users want to create a skill from scratch, edit or improve an existing skill, or refine a skill's description so it triggers more reliably. Also use when a user asks to "turn this workflow into a skill", "save these instructions as a skill", or "make the agent better at X".
---

# Skill Creator

A skill for creating and iteratively improving JavaClaw skills.

Skills in JavaClaw live at `workspace/skills/<skill-name>/SKILL.md` and are loaded dynamically by the `SkillsTool` at runtime. The agent picks them up automatically — no restart needed.

The core loop:
1. Understand what the skill should do
2. Write a draft `SKILL.md`
3. Walk through 2-3 test scenarios and evaluate the output inline
4. Refine based on feedback
5. Repeat until the user is happy

Jump in wherever the user is. If they already have a draft, skip straight to testing. If they just have a vague idea, interview them first.

---

## Creating a skill

### Capture Intent

Start by understanding what the user actually wants. If the conversation already shows a workflow the user wants to capture (a sequence of steps, tools used, corrections made), extract the answers from that — then fill gaps with the user.

Key questions:
1. What should this skill enable the agent to do?
2. When should it trigger? (what kinds of user messages)
3. What does a good output look like?

### Interview

Ask about edge cases, expected inputs, success criteria, and any context the agent will need. Don't start writing until you have enough to write something useful — but don't over-interview either. If you can make a reasonable assumption, make it and note it.

### Write the SKILL.md

Create `workspace/skills/<skill-name>/SKILL.md` with:

- **`name`**: kebab-case identifier matching the directory name
- **`description`**: The primary triggering mechanism. Include _what_ the skill does AND _when_ to use it. The agent decides whether to load a skill based solely on this field — so be specific and make it slightly "pushy". Instead of "Helps with data analysis", write "Helps with data analysis. Use this skill whenever the user asks about datasets, CSV files, charts, or wants to understand or transform data — even if they don't say 'analysis'."
- **Body**: Step-by-step instructions for what the agent should do. Explain the _why_ behind important steps so the agent can apply judgment, not just follow rules mechanically.

#### ⚠️ CRITICAL: YAML Front Matter Validation

**BEFORE saving the SKILL.md, you MUST validate the YAML front matter!**

The YAML front matter (between `---` markers at the top) is parsed by JavaClaw. If it's malformed or missing required fields, the app will crash with `NullPointerException`.

**Validation checklist:**
1. ✅ YAML front matter exists (starts with `---` on line 1)
2. ✅ `name:` field exists and matches the directory name exactly
3. ✅ `description:` field exists and is NOT empty/null
4. ✅ No syntax errors in YAML (proper indentation, no tabs)
5. ✅ Closing `---` exists after the description

**CORRECT example:**
```yaml
---
name: skill-creator
description: Create new skills, modify and improve existing skills for JavaClaw. Use when users want to create a skill from scratch...
---
```

**BROKEN examples (will crash the app!):**
```yaml
---
name: skill-creator
# Missing description - NULL POINTER EXCEPTION!
---
```

```yaml
---
description: Create new skills...
# Missing name field - may cause crashes!
---
```

```yaml
---
name: skill-creator
description: 
---
# Empty description - NULL POINTER EXCEPTION!
```

#### 🔧 Automated Validation Command

**Run this Python script BEFORE saving to catch errors:**

```bash
cd /home/radxa/agentrunr/workspace/skills/<skill-name> && python3 -c "
import re
with open('SKILL.md', 'r') as f:
    content = f.read()
    
# Check YAML front matter
if not content.startswith('---'):
    print('❌ ERROR: File must start with ---')
    exit(1)
    
parts = content.split('---')
if len(parts) < 3:
    print('❌ ERROR: Missing closing ---')
    exit(1)
    
yaml_content = parts[1]
    
# Check name field
if 'name:' not in yaml_content:
    print('❌ ERROR: Missing name field')
    exit(1)
    
name_match = re.search(r'name:\s*(\S+)', yaml_content)
if not name_match or not name_match.group(1).strip():
    print('❌ ERROR: name field is empty or null')
    exit(1)
    
# Check description field
if 'description:' not in yaml_content:
    print('❌ ERROR: Missing description field')
    exit(1)
    
desc_match = re.search(r'description:\s*(.+)', yaml_content)
if not desc_match or not desc_match.group(1).strip():
    print('❌ ERROR: description field is empty or null')
    exit(1)
    
print('✅ YAML validation passed!')
print(f'   name: {name_match.group(1).strip()}')
print(f'   description: {desc_match.group(1).strip()[:50]}...')
"
```

**If validation fails:**
- Fix the error immediately
- Re-run validation until it passes
- ONLY then save the file

#### Anatomy of a skill

```
workspace/skills/
└── skill-name/
    └── SKILL.md   ← required; keep under ~300 lines
```

JavaClaw skills are single-file. The agent reads `SKILL.md` when the skill triggers. Keep it focused and readable — a dense wall of text is harder to follow than clear, structured instructions.

#### Writing patterns

Use imperative form. Explain reasoning where it matters.

**Output format example:**
```markdown
## Report structure
Use this template every time:
# [Title]
## Summary
## Details
## Next steps
```

**Example pattern:**
```markdown
## Commit message format
User said: "Added login with Google"
Write: feat(auth): add Google OAuth login
```

#### Principles

- Lean over comprehensive. A shorter, well-reasoned skill beats a long checklist.
- Explain the *why* so the agent can adapt to situations the skill didn't explicitly anticipate.
- Avoid ALL CAPS MUSTs and rigid structures where possible — trust the agent's judgment when given good context.
- Skills must not contain malware, exploit code, or content that would surprise the user given the skill's stated purpose.

---

## Testing the skill

### ⚠️ MANDATORY 3-Step Testing Process

**BEFORE declaring a skill complete, you MUST run all 3 steps:**

#### Step 1: YAML Validation (MANDATORY - prevents crashes!)

```bash
cd /home/radxa/agentrunr/workspace/skills/<skill-name> && python3 -c "
import re
with open('SKILL.md', 'r') as f:
    content = f.read()
    
if not content.startswith('---'):
    print('❌ ERROR: File must start with ---')
    exit(1)
    
parts = content.split('---')
if len(parts) < 3:
    print('❌ ERROR: Missing closing ---')
    exit(1)
    
yaml_content = parts[1]

if 'name:' not in yaml_content:
    print('❌ ERROR: Missing name field')
    exit(1)
    
name_match = re.search(r'name:\s*(\S+)', yaml_content)
if not name_match or not name_match.group(1).strip():
    print('❌ ERROR: name field is empty or null')
    exit(1)
    
if 'description:' not in yaml_content:
    print('❌ ERROR: Missing description field')
    exit(1)
    
desc_match = re.search(r'description:\s*(.+)', yaml_content)
if not desc_match or not desc_match.group(1).strip():
    print('❌ ERROR: description field is empty or null')
    exit(1)
    
print('✅ YAML validation passed!')
"
```

**Expected output:** `✅ YAML validation passed!`

**If this step fails:** DO NOT proceed. Fix the YAML errors first.

#### Step 2: Functional Testing

After writing a draft, come up with 2-3 realistic test prompts — things a real user would actually say. Share them and confirm with the user before proceeding.

For each test prompt, read the skill and follow its instructions yourself to complete the task. Present the output to the user and ask for feedback:

> "Here's what the agent would do for: _[prompt]_. Does this look right? Anything you'd change?"

This is intentionally lightweight — you wrote the skill and you're running it, so you have full context. The goal is a quick sanity check, not a rigorous benchmark. The human review is what matters.

#### Step 3: Verify App Doesn't Crash

**Final safety check before completing the task:**

1. Ensure the skill file is saved
2. Verify no syntax errors in YAML (Step 1 passed)
3. Confirm `name` and `description` are NOT empty
4. **Optional but recommended:** Restart JavaClaw and verify the skill loads without errors

**Checklist before marking task complete:**
- [ ] ✅ Step 1: YAML validation passed
- [ ] ✅ Step 2: Functional tests completed with user feedback
- [ ] ✅ Step 3: No crashes, skill loads successfully

---

This is intentionally lightweight — you wrote the skill and you're running it, so you have full context. The goal is a quick sanity check, not a rigorous benchmark. The human review is what matters.

---

## Improving the skill

After the user reviews, update the skill based on their feedback. A few principles:

1. **Generalize, don't patch.** If a test case revealed a gap, think about what the underlying issue is and fix that — don't just add a special case for the exact example.

2. **Stay lean.** Remove instructions that aren't pulling their weight. If the agent is already doing something naturally, you don't need to spell it out.

3. **Explain the why.** If you find yourself adding a rigid rule, ask whether you could instead explain the reasoning so the agent understands _why_ and can apply it flexibly.

After updating, re-run the same test prompts (and any new ones) and repeat until:
- The user is satisfied
- There's nothing more to improve

---

## Description optimization

The `description` field is the only thing the agent sees when deciding whether to load a skill. A weak description means the skill never triggers; an overly broad one means it triggers when it shouldn't.

After finishing the skill, review the description with the user:

1. Identify 3-5 kinds of user messages that _should_ trigger this skill — including indirect ones that don't name the skill explicitly.
2. Identify 2-3 near-miss cases — messages that share keywords but actually need something different.
3. Revise the description to clearly cover the should-trigger cases and implicitly exclude the near-misses.

Present before/after to the user and confirm.

---

## Updating an existing skill

If the user wants to improve an existing skill rather than create a new one:
- Read the current `SKILL.md` first before suggesting any changes.
- Keep the skill's `name` and directory unchanged.
- Apply the same test → feedback → refine loop as above.

---