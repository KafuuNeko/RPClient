# RPClient Privacy Policy

Last updated: June 16, 2026

Effective date: June 16, 2026

This Privacy Policy applies to the RPClient Android application and its related features. RPClient is a local-first AI roleplay chat client designed to help users manage character cards, lorebooks, chat history, prompt settings, and model provider configurations on their own devices.

Please read this Privacy Policy carefully before using RPClient. If you do not agree with any part of this Policy, please stop using the application. This document is a project privacy notice template and does not constitute legal advice. Before publishing the app commercially or distributing it in specific jurisdictions, you should have this Policy reviewed by a qualified compliance or legal professional.

## 1. Data Controller

Data controller: [Please fill in the operator or developer name]

App name: RPClient

Contact email: kafuuneko@gmail.com

Project repository: https://github.com/KafuuNeko/RPClient

If the controller information in this Policy differs from information shown on an app store page, official website, or other public channel, the latest public information should prevail.

## 2. Information We Process

RPClient stores chat history, characters, lorebooks, model settings, and app preferences locally on your device by default. Except when you configure and call a third-party model provider, import or export files, use system backup or transfer features, or where required by law, the RPClient developer does not actively collect, upload, or centrally store your personal information.

To provide app features, RPClient may process the following information locally:

1. App settings and preferences, such as language, theme, selected model provider, streaming settings, summary settings, prompt templates, username, user persona description, and Regex script authorization status.
2. Model provider configuration, such as provider name, base URL, protocol type, model name, API key, custom headers, temperature, top-p, maximum output tokens, and context token budget.
3. Character and lorebook data, such as character names, avatars, tags, descriptions, personality, scenario, greetings, example dialogues, creator notes, lorebook entries, trigger keywords, priority, depth, probability, and token budget.
4. Chat and group chat data, such as single chat messages, group chat messages, session settings, long-term summary memory, group members, message timestamps, and message roles.
5. Imported and exported file data, such as Character Card JSON/PNG files, lorebook JSON files, Regex script JSON files, avatars, and other compatible files that you choose to import or export.
6. Debug log data. If you enable debug mode, RPClient stores raw LLM request JSON and response JSON locally. These logs may include prompts, chat content, character settings, lorebook content, and model responses. When debug mode is disabled, RPClient stops writing new debug logs.

We process this information to:

1. Create, edit, save, and display characters, lorebooks, sessions, and group chats.
2. Build prompts and send generation requests to the model provider configured by you.
3. Save your app settings, prompt templates, model parameters, and authorization preferences.
4. Support import, export, and compatibility features for character cards, lorebooks, and Regex scripts.
5. Support prompt inspection, token budgeting, summary memory, request logging, and troubleshooting.

## 3. Third-Party Model Providers

RPClient does not provide large language model services. When you configure and enable a model provider, RPClient sends necessary request data directly to the model provider or custom endpoint configured by you.

The data sent to a model provider may include:

1. API keys or authentication headers.
2. Model names, generation parameters, and streaming settings.
3. Prompt content required to generate a response, including character settings, activated lorebook content, chat history, summary memory, user input, and system prompts.
4. Request data required for connection tests, summary generation, continuation, regeneration, or group chat replies.

Built-in templates may refer to providers such as OpenAI, Google Gemini, Anthropic, DeepSeek, and OpenRouter. RPClient also supports OpenAI-compatible APIs and custom proxy endpoints. Third-party providers process your request data under their own privacy policies, terms of service, and data processing rules. The RPClient developer cannot control how third-party providers store, use, or delete your data.

Only configure service addresses and proxy endpoints that you trust. Avoid entering real identity numbers, bank card numbers, addresses, contact details, health information, precise location, minors' information, sensitive identity information, or any other content that you do not want a third-party service to process.

## 4. Android Permissions

RPClient currently uses the following Android permission:

1. Internet access (`android.permission.INTERNET`): used to connect to the model provider or custom endpoint configured by you, send model requests, and receive responses.

RPClient currently does not request location, contacts, SMS, phone, camera, microphone, or similar permissions. If future versions add new permissions, we will explain their purposes in an updated Privacy Policy and request authorization where required by the operating system.

## 5. Storage and Retention

RPClient stores app data locally by default, including local database records, app preferences, and private app files. This data is generally retained until you delete it in the app, clear app data through the operating system, or uninstall the app.

If your device enables Android, device manufacturer, or account-level backup, transfer, or cloud sync features, some RPClient app data may be backed up or transferred according to system rules. This process is controlled by the operating system, device manufacturer, or account service provider. You can manage these settings in your system settings.

Files that you export, such as character cards, lorebooks, Regex scripts, or other files, are stored in the location you choose. You are responsible for managing and sharing exported files.

Data sent to third-party model providers is stored, retained, deleted, and secured according to the rules of the relevant provider. Please review the privacy policy and terms of service of each provider you use.

## 6. Sharing, Transfer, and Disclosure

Except in the following cases, the RPClient developer does not actively share, transfer, or publicly disclose your personal information:

1. You configure a model provider and initiate a model request, and RPClient sends request data to that provider at your direction.
2. You export, copy, or share character cards, lorebooks, chat content, request logs, or other files.
3. Disclosure is required by applicable laws, courts, administrative authorities, or regulators.
4. Disclosure is necessary to protect the personal or property safety, lawful rights, or app security of you, other users, the developer, or the public, and is permitted by applicable law.

If a merger, division, acquisition, asset transfer, or similar transaction occurs and involves the transfer of personal information, we will require the new processor to continue complying with this Policy or notify you again and obtain authorization where required.

## 7. Security

RPClient follows a local-first approach and seeks to minimize unnecessary data uploads. App data is stored in Android private app storage and the local database, which are normally accessible only by the app or by environments with sufficient system-level access.

No system or network environment can guarantee absolute security. To reduce risk, we recommend that you:

1. Set a device lock screen password and avoid using the app in rooted, modified, or untrusted environments.
2. Avoid sending sensitive content to untrusted model providers, proxy endpoints, or network services.
3. Regularly review and rotate API keys, and do not publish API keys in issues, screenshots, exported files, or chat content.
4. Use debug mode carefully. Debug logs may contain full request and response content. After troubleshooting, consider clearing debug logs.
5. Be cautious when importing character cards, lorebooks, and Regex scripts from unknown sources, especially scripts that may affect prompts or generated output.

## 8. Your Rights and Choices

You can manage your local data through RPClient features:

1. Access and copy: you can view characters, lorebooks, sessions, request logs, and settings in the app. Some data can be copied through export features.
2. Correction: you can edit characters, lorebooks, model provider settings, prompt templates, and session-related content.
3. Deletion: you can delete characters, lorebooks, sessions, group chats, model provider settings, request logs, and other data. You can also clear app data through system settings or uninstall the app.
4. Withdrawal or stopping processing: you can delete API keys, disable or delete model providers, turn off debug mode, disable relevant features, or stop using the app.
5. Inquiries and feedback: you can contact the developer through the email address or GitHub repository listed in this Policy.

Because RPClient does not create developer-hosted accounts or centrally store your app data by default, the developer usually cannot directly access, export, correct, or delete data stored locally on your device. You must perform those actions on your own device.

## 9. Minors

RPClient is not specifically directed to minors. Minors should use the app only with the consent and guidance of a parent or guardian.

If you are under the age of 14, please use RPClient only with the consent and supervision of your guardian, and do not enter your real name, contact details, school, address, precise location, identity documents, photos, health information, or other sensitive personal information. Guardians may contact us using the contact information in this Policy if they believe a minor's personal information has been processed improperly.

## 10. Sensitive Information Notice

AI roleplay content, chat history, character settings, lorebooks, summary memory, Regex scripts, and request logs may contain sensitive or highly private information. Unless necessary, do not enter:

1. Identity document numbers, bank card numbers, payment accounts, passwords, verification codes, API keys, or similar credentials.
2. Real addresses, contact details, precise location, workplace, school, class, or other directly identifying information.
3. Health information, biometric information, religious beliefs, sensitive identity information, minors' information, or similar sensitive data.
4. Any other person's personal information, private content, or copyrighted content without authorization.

If you send such information to a third-party model provider, that provider may process it under its own rules. Please assess the risk before sending it.

## 11. Updates to This Policy

We may update this Policy due to changes in app features, laws and regulations, or compliance requirements. Updated versions may be shown in the app, project repository, release notes, or other appropriate channels.

If an update materially affects your rights or how personal information is processed, we will provide a more prominent notice where reasonably possible. Your continued use of RPClient means that you have read and accepted the updated Policy.

## 12. Contact Us

If you have questions, comments, or requests regarding this Policy or personal information protection, please contact us at:

Email: kafuuneko@gmail.com

GitHub: https://github.com/KafuuNeko/RPClient

We will respond to valid requests as soon as reasonably possible. For security reasons, we may need information sufficient to verify that the request relates to a specific device, local data, or project issue.

