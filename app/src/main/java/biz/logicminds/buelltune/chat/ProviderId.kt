/*
 EcmDroid - Android Diagnostic Tool for Buell Motorcycles
 Copyright (C) 2012 by Michel Marti

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 3
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package biz.logicminds.buelltune.chat

/**
 * The LLM providers the chat agent can be configured against (KD4): every
 * provider is available from v1, not "Anthropic first, others added later."
 * Kimi/Moonshot is reached through [OPENROUTER] rather than a dedicated
 * entry, since Koog does not name it as a first-class provider.
 */
enum class ProviderId {
    ANTHROPIC,
    OPENAI,
    GOOGLE,
    DEEPSEEK,
    OPENROUTER,
    OLLAMA,
    BEDROCK,
}
