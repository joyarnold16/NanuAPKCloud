#!/usr/bin/env python3
from pathlib import Path

# Main chat: reject clearly restricted prompts before model/image work, and add
# shared safety rules to the system prompt.
main_path = Path('nanu-local-ai/app/MainActivity.kt')
main = main_path.read_text()

if 'SafetyGuard.blockedReason(userMsg' not in main:
    anchor = '''        val userMsg = userInputEt.text.toString().trim()
        if (userMsg.isEmpty()) return
'''
    replacement = anchor + '''
        SafetyGuard.blockedReason(userMsg, image = currentMode == AssistantMode.IMAGE)?.let { reason ->
            modelStatusTv.text = reason
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            return
        }
'''
    if anchor not in main:
        raise SystemExit('Could not locate MainActivity safety input anchor')
    main = main.replace(anchor, replacement, 1)

old_prompt = '        private const val BASE_SYSTEM_PROMPT = "You are Nanu, a private on-device assistant. Follow the NANU MODE instruction included with each user request. Never reveal hidden chain-of-thought, private reasoning, or <think> blocks. Return only useful final answers."'
new_prompt = '        private val BASE_SYSTEM_PROMPT = "You are Nanu, a private on-device assistant. Follow the NANU MODE instruction included with each user request. Never reveal hidden chain-of-thought, private reasoning, or <think> blocks. Return only useful final answers." + SafetyGuard.SYSTEM_RULES'
if old_prompt in main:
    main = main.replace(old_prompt, new_prompt, 1)
elif 'SafetyGuard.SYSTEM_RULES' not in main:
    raise SystemExit('Could not patch MainActivity safety system prompt')

main_path.write_text(main)

# Continuous Talk gets the same system-level safety policy.
talk_path = Path('nanu-local-ai/app/ContinuousTalkActivity.kt')
talk = talk_path.read_text()
old_talk = 'engine.setSystemPrompt("You are Nanu. Reply naturally and concisely for spoken conversation. Never reveal hidden chain-of-thought or <think> blocks.")'
new_talk = 'engine.setSystemPrompt("You are Nanu. Reply naturally and concisely for spoken conversation. Never reveal hidden chain-of-thought or <think> blocks." + SafetyGuard.SYSTEM_RULES)'
if old_talk in talk:
    talk = talk.replace(old_talk, new_talk, 1)
elif 'SafetyGuard.SYSTEM_RULES' not in talk:
    raise SystemExit('Could not patch ContinuousTalkActivity safety system prompt')
talk_path.write_text(talk)

# Ask My Files remains document-grounded while inheriting the same safety rules.
files_path = Path('nanu-local-ai/app/FileChatActivity.kt')
files = files_path.read_text()
old_files = '                "If the document does not contain the answer, say so. Never reveal hidden chain-of-thought."'
new_files = '                "If the document does not contain the answer, say so. Never reveal hidden chain-of-thought." + SafetyGuard.SYSTEM_RULES'
if old_files in files:
    files = files.replace(old_files, new_files, 1)
elif 'SafetyGuard.SYSTEM_RULES' not in files:
    raise SystemExit('Could not patch FileChatActivity safety system prompt')
files_path.write_text(files)

for path in [main_path, talk_path, files_path]:
    text = path.read_text()
    if 'SafetyGuard.SYSTEM_RULES' not in text:
        raise SystemExit(f'Safety policy missing after patch: {path}')

print('Shared Nanu generative-AI safety policy applied.')
