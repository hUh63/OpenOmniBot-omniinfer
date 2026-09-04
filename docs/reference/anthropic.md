# 提示词缓存

通过允许从提示词中的特定前缀恢复来优化 API 使用。这显著减少了重复任务或具有一致元素的提示词的处理时间和成本。

---

提示词缓存通过允许从提示词中的特定前缀恢复来优化您的 API 使用。这显著减少了重复任务或具有一致元素的提示词的处理时间和成本。

<Note>
This feature is eligible for [Zero Data Retention (ZDR)](/docs/en/build-with-claude/api-and-data-retention). When your organization has a ZDR arrangement, data sent through this feature is not stored after the API response is returned.
</Note>

有两种方式可以启用提示词缓存：

- **[自动缓存](#automatic-caching)**：在请求的顶级添加单个 `cache_control` 字段。系统自动将缓存断点应用于最后一个可缓存块，并随着对话增长而向前移动。最适合多轮对话，其中不断增长的消息历史应该自动缓存。
- **[显式缓存断点](#explicit-cache-breakpoints)**：直接在单个内容块上放置 `cache_control`，以对缓存内容进行细粒度控制。

最简单的开始方式是使用自动缓存：

<CodeGroup>

```bash Shell
curl https://api.anthropic.com/v1/messages \
  -H "content-type: application/json" \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -d '{
    "model": "claude-opus-4-6",
    "max_tokens": 1024,
    "cache_control": {"type": "ephemeral"},
    "system": "You are an AI assistant tasked with analyzing literary works. Your goal is to provide insightful commentary on themes, characters, and writing style.",
    "messages": [
      {
        "role": "user",
        "content": "Analyze the major themes in Pride and Prejudice."
      }
    ]
  }'
```

```bash CLI
ant messages create --transform usage <<'YAML'
model: claude-opus-4-6
max_tokens: 1024
cache_control:
  type: ephemeral
system: >-
  You are an AI assistant tasked with analyzing literary works. Your goal is
  to provide insightful commentary on themes, characters, and writing style.
messages:
  - role: user
    content: Analyze the major themes in Pride and Prejudice.
YAML
```

```python Python hidelines={1..2}
import anthropic

client = anthropic.Anthropic()

response = client.messages.create(
    model="claude-opus-4-6",
    max_tokens=1024,
    cache_control={"type": "ephemeral"},
    system="You are an AI assistant tasked with analyzing literary works. Your goal is to provide insightful commentary on themes, characters, and writing style.",
    messages=[
        {
            "role": "user",
            "content": "Analyze the major themes in 'Pride and Prejudice'.",
        }
    ],
)
print(response.usage.model_dump_json())
```

```typescript TypeScript hidelines={1..2}
import Anthropic from "@anthropic-ai/sdk";

const client = new Anthropic();

const response = await client.messages.create({
  model: "claude-opus-4-6",
  max_tokens: 1024,
  cache_control: { type: "ephemeral" },
  system:
    "You are an AI assistant tasked with analyzing literary works. Your goal is to provide insightful commentary on themes, characters, and writing style.",
  messages: [
    {
      role: "user",
      content: "Analyze the major themes in 'Pride and Prejudice'."
    }
  ]
});
console.log(response.usage);
```

```csharp C# hidelines={1..9,-2..}
using System;
using System.Threading.Tasks;
using Anthropic;
using Anthropic.Models.Messages;

class Program
{
    static async Task Main(string[] args)
    {
        AnthropicClient client = new();

        var parameters = new MessageCreateParams
        {
            Model = Model.ClaudeOpus4_6,
            MaxTokens = 1024,
            CacheControl = new CacheControlEphemeral(),
            System = "You are an AI assistant tasked with analyzing literary works. Your goal is to provide insightful commentary on themes, characters, and writing style.",
            Messages =
            [
                new()
                {
                    Role = Role.User,
                    Content = "Analyze the major themes in 'Pride and Prejudice'."
                }
            ]
        };

        var message = await client.Messages.Create(parameters);
        Console.WriteLine(message.Usage);
    }
}
```

```go Go hidelines={1..11,-1}
package main

import (
	"context"
	"fmt"
	"log"

	"github.com/anthropics/anthropic-sdk-go"
)

func main() {
	client := anthropic.NewClient()

	response, err := client.Messages.New(context.TODO(), anthropic.MessageNewParams{
		Model:        anthropic.ModelClaudeOpus4_6,
		MaxTokens:    1024,
		CacheControl: anthropic.NewCacheControlEphemeralParam(),
		System: []anthropic.TextBlockParam{
			{Text: "You are an AI assistant tasked with analyzing literary works. Your goal is to provide insightful commentary on themes, characters, and writing style."},
		},
		Messages: []anthropic.MessageParam{
			anthropic.NewUserMessage(anthropic.NewTextBlock("Analyze the major themes in 'Pride and Prejudice'.")),
		},
	})
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(response.Usage)
}
```

```java Java hidelines={1..2,4..10,-2..}
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;

public class PromptCachingExample {

  public static void main(String[] args) {
    AnthropicClient client = AnthropicOkHttpClient.fromEnv();

    MessageCreateParams params = MessageCreateParams.builder()
        .model(Model.CLAUDE_OPUS_4_6)
        .maxTokens(1024)
        .cacheControl(CacheControlEphemeral.builder().build())
        .system("You are an AI assistant tasked with analyzing literary works. Your goal is to provide insightful commentary on themes, characters, and writing style.")
        .addUserMessage("Analyze the major themes in 'Pride and Prejudice'.")
        .build();

    Message message = client.messages().create(params);
    System.out.println(message.usage());
  }
}
```

```php PHP hidelines={1..3,5}
<?php

use Anthropic\Client;
use Anthropic\Messages\CacheControlEphemeral;

$client = new Client(apiKey: getenv("ANTHROPIC_API_KEY"));

$response = $client->messages->create(
    maxTokens: 1024,
    messages: [
        ['role' => 'user', 'content' => "Analyze the major themes in 'Pride and Prejudice'."]
    ],
    model: 'claude-opus-4-6',
    cacheControl: CacheControlEphemeral::with(),
    system: "You are an AI assistant tasked with analyzing literary works. Your goal is to provide insightful commentary on themes, characters, and writing style.",
);
echo json_encode($response->usage);
```

```ruby Ruby hidelines={1..2}
require "anthropic"

client = Anthropic::Client.new

response = client.messages.create(
  model: "claude-opus-4-6",
  max_tokens: 1024,
  cache_control: {type: "ephemeral"},
  system: "You are an AI assistant tasked with analyzing literary works. Your goal is to provide insightful commentary on themes, characters, and writing style.",
  messages: [
    {
      role: "user",
      content: "Analyze the major themes in 'Pride and Prejudice'."
    }
  ]
)
puts response.usage
```
</CodeGroup>

使用自动缓存，系统会缓存所有内容直到并包括最后一个可缓存块。在后续请求中使用相同前缀时，缓存的内容会自动重用。

---

## 提示词缓存如何工作

当您发送启用了提示词缓存的请求时：

1. 系统检查提示词前缀（直到指定的缓存断点）是否已从最近的查询中缓存。
2. 如果找到，它使用缓存版本，减少处理时间和成本。
3. 否则，它处理完整提示词，并在响应开始后缓存前缀。

这对以下情况特别有用：
- 包含许多示例的提示词
- 大量上下文或背景信息
- 具有一致指令的重复任务
- 长多轮对话

默认情况下，缓存的生命周期为 5 分钟。每次使用缓存的内容时，缓存都会以无额外成本的方式刷新。

<Note>
如果您发现 5 分钟太短，Anthropic 还提供 1 小时缓存时长 [需额外付费](#pricing)。

有关更多信息，请参阅 [1 小时缓存时长](#1-hour-cache-duration)。
</Note>

<Tip>
  **提示词缓存缓存完整前缀**

提示词缓存引用整个提示词 - `tools`、`system` 和 `messages`（按该顺序）直到并包括用 `cache_control` 指定的块。

</Tip>

---

## 定价

提示词缓存引入了新的定价结构。下表显示了每个支持的模型的每百万个令牌的价格：

| Model             | Base Input Tokens | 5m Cache Writes | 1h Cache Writes | Cache Hits & Refreshes | Output Tokens |
|-------------------|-------------------|-----------------|-----------------|----------------------|---------------|
| Claude Opus 4.6     | $5 / MTok         | $6.25 / MTok    | $10 / MTok      | $0.50 / MTok | $25 / MTok    |
| Claude Opus 4.5   | $5 / MTok         | $6.25 / MTok    | $10 / MTok      | $0.50 / MTok | $25 / MTok    |
| Claude Opus 4.1   | $15 / MTok        | $18.75 / MTok   | $30 / MTok      | $1.50 / MTok | $75 / MTok    |
| Claude Opus 4     | $15 / MTok        | $18.75 / MTok   | $30 / MTok      | $1.50 / MTok | $75 / MTok    |
| Claude Sonnet 4.6   | $3 / MTok         | $3.75 / MTok    | $6 / MTok       | $0.30 / MTok | $15 / MTok    |
| Claude Sonnet 4.5   | $3 / MTok         | $3.75 / MTok    | $6 / MTok       | $0.30 / MTok | $15 / MTok    |
| Claude Sonnet 4   | $3 / MTok         | $3.75 / MTok    | $6 / MTok       | $0.30 / MTok | $15 / MTok    |
| Claude Sonnet 3.7 ([deprecated](/docs/en/about-claude/model-deprecations)) | $3 / MTok         | $3.75 / MTok    | $6 / MTok       | $0.30 / MTok | $15 / MTok    |
| Claude Haiku 4.5  | $1 / MTok         | $1.25 / MTok    | $2 / MTok       | $0.10 / MTok | $5 / MTok     |
| Claude Haiku 3.5  | $0.80 / MTok      | $1 / MTok       | $1.6 / MTok     | $0.08 / MTok | $4 / MTok     |
| Claude Opus 3 ([deprecated](/docs/en/about-claude/model-deprecations))    | $15 / MTok        | $18.75 / MTok   | $30 / MTok      | $1.50 / MTok | $75 / MTok    |
| Claude Haiku 3    | $0.25 / MTok      | $0.30 / MTok    | $0.50 / MTok    | $0.03 / MTok | $1.25 / MTok  |

<Note>
上表反映了提示词缓存的以下定价倍数：
- 5 分钟缓存写入令牌是基础输入令牌价格的 1.25 倍
- 1 小时缓存写入令牌是基础输入令牌价格的 2 倍
- 缓存读取令牌是基础输入令牌价格的 0.1 倍

这些倍数与其他定价修饰符（如 Batch API 折扣和数据驻留）叠加。有关完整详情，请参阅 [定价](/docs/zh-CN/about-claude/pricing)。
</Note>

---

## 支持的模型

提示词缓存（自动和显式）在所有 [活跃 Claude 模型](/docs/zh-CN/about-claude/models/overview) 上受支持。

---

## 自动缓存

自动缓存是启用提示词缓存的最简单方式。不是在单个内容块上放置 `cache_control`，而是在请求体的顶级添加单个 `cache_control` 字段。系统自动将缓存断点应用于最后一个可缓存块。

<CodeGroup>

```bash Shell
curl https://api.anthropic.com/v1/messages \
  -H "content-type: application/json" \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -d '{
    "model": "claude-opus-4-6",
    "max_tokens": 1024,
    "cache_control": {"type": "ephemeral"},
    "system": "You are a helpful assistant that remembers our conversation.",
    "messages": [
      {"role": "user", "content": "My name is Alex. I work on machine learning."},
      {"role": "assistant", "content": "Nice to meet you, Alex! How can I help with your ML work today?"},
      {"role": "user", "content": "What did I say I work on?"}
    ]
  }'
```

```bash CLI
ant messages create --transform usage <<'YAML'
model: claude-opus-4-6
max_tokens: 1024
cache_control:
  type: ephemeral
system: You are a helpful assistant that remembers our conversation.
messages:
  - role: user
    content: My name is Alex. I work on machine learning.
  - role: assistant
    content: Nice to meet you, Alex! How can I help with your ML work today?
  - role: user
    content: What did I say I work on?
YAML
```

```python Python hidelines={1..2}
import anthropic

client = anthropic.Anthropic()

response = client.messages.create(
    model="claude-opus-4-6",
    max_tokens=1024,
    cache_control={"type": "ephemeral"},
    system="You are a helpful assistant that remembers our conversation.",
    messages=[
        {"role": "user", "content": "My name is Alex. I work on machine learning."},
        {
            "role": "assistant",
            "content": "Nice to meet you, Alex! How can I help with your ML work today?",
        },
        {"role": "user", "content": "What did I say I work on?"},
    ],
)
print(response.usage.model_dump_json())
```

```typescript TypeScript hidelines={1..2}
import Anthropic from "@anthropic-ai/sdk";

const client = new Anthropic();

const response = await client.messages.create({
  model: "claude-opus-4-6",
  max_tokens: 1024,
  cache_control: { type: "ephemeral" },
  system: "You are a helpful assistant that remembers our conversation.",
  messages: [
    { role: "user", content: "My name is Alex. I work on machine learning." },
    {
      role: "assistant",
      content: "Nice to meet you, Alex! How can I help with your ML work today?"
    },
    { role: "user", content: "What did I say I work on?" }
  ]
});
console.log(response.usage);
```

```csharp C# hidelines={1..9,-2..}
using System;
using System.Threading.Tasks;
using Anthropic;
using Anthropic.Models.Messages;

class Program
{
    static async Task Main(string[] args)
    {
        AnthropicClient client = new();

        var parameters = new MessageCreateParams
        {
            Model = Model.ClaudeOpus4_6,
            MaxTokens = 1024,
            CacheControl = new CacheControlEphemeral(),
            System = "You are a helpful assistant that remembers our conversation.",
            Messages =
            [
                new()
                {
                    Role = Role.User,
                    Content = "My name is Alex. I work on machine learning."
                },
                new()
                {
                    Role = Role.Assistant,
                    Content = "Nice to meet you, Alex! How can I help with your ML work today?"
                },
                new()
                {
                    Role = Role.User,
                    Content = "What did I say I work on?"
                }
            ]
        };

        var message = await client.Messages.Create(parameters);
        Console.WriteLine(message.Usage);
    }
}
```

```go Go hidelines={1..11,-1}
package main

import (
	"context"
	"fmt"
	"log"

	"github.com/anthropics/anthropic-sdk-go"
)

func main() {
	client := anthropic.NewClient()

	response, err := client.Messages.New(context.TODO(), anthropic.MessageNewParams{
		Model:        anthropic.ModelClaudeOpus4_6,
		MaxTokens:    1024,
		CacheControl: anthropic.NewCacheControlEphemeralParam(),
		System: []anthropic.TextBlockParam{
			{Text: "You are a helpful assistant that remembers our conversation."},
		},
		Messages: []anthropic.MessageParam{
			anthropic.NewUserMessage(anthropic.NewTextBlock("My name is Alex. I work on machine learning.")),
			anthropic.NewAssistantMessage(anthropic.NewTextBlock("Nice to meet you, Alex! How can I help with your ML work today?")),
			anthropic.NewUserMessage(anthropic.NewTextBlock("What did I say I work on?")),
		},
	})
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(response.Usage)
}
```

```java Java hidelines={1..2,4..10,-2..}
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;

public class AutomaticCachingExample {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_6)
                .maxTokens(1024)
                .cacheControl(CacheControlEphemeral.builder().build())
                .system("You are a helpful assistant that remembers our conversation.")
                .addUserMessage("My name is Alex. I work on machine learning.")
                .addAssistantMessage("Nice to meet you, Alex! How can I help with your ML work today?")
                .addUserMessage("What did I say I work on?")
                .build();

        Message message = client.messages().create(params);
        System.out.println(message.usage());
    }
}
```

```php PHP hidelines={1..3,5}
<?php

use Anthropic\Client;
use Anthropic\Messages\CacheControlEphemeral;

$client = new Client(apiKey: getenv("ANTHROPIC_API_KEY"));

$response = $client->messages->create(
    maxTokens: 1024,
    messages: [
        ['role' => 'user', 'content' => 'My name is Alex. I work on machine learning.'],
        ['role' => 'assistant', 'content' => 'Nice to meet you, Alex! How can I help with your ML work today?'],
        ['role' => 'user', 'content' => 'What did I say I work on?'],
    ],
    model: 'claude-opus-4-6',
    cacheControl: CacheControlEphemeral::with(),
    system: 'You are a helpful assistant that remembers our conversation.',
);
echo json_encode($response->usage);
```

```ruby Ruby hidelines={1..2}
require "anthropic"

client = Anthropic::Client.new

response = client.messages.create(
  model: "claude-opus-4-6",
  max_tokens: 1024,
  cache_control: {type: "ephemeral"},
  system: "You are a helpful assistant that remembers our conversation.",
  messages: [
    {role: "user", content: "My name is Alex. I work on machine learning."},
    {role: "assistant", content: "Nice to meet you, Alex! How can I help with your ML work today?"},
    {role: "user", content: "What did I say I work on?"}
  ]
)
puts response.usage
```
</CodeGroup>

### 自动缓存在多轮对话中的工作原理

使用自动缓存，缓存点会随着对话增长而自动向前移动。每个新请求都会缓存直到最后一个可缓存块的所有内容，而之前的内容从缓存中读取。

| 请求 | 内容 | 缓存行为 |
|---------|---------|----------------|
| 请求 1 | 系统 <br/> + 用户(1) + 助手(1) <br/> + **用户(2)** ◀ 缓存 | 所有内容写入缓存 |
| 请求 2 | 系统 <br/> + 用户(1) + 助手(1) <br/> + 用户(2) + 助手(2) <br/> + **用户(3)** ◀ 缓存 | 系统到用户(2) 从缓存读取；<br/> 助手(2) + 用户(3) 写入缓存 |
| 请求 3 | 系统 <br/> + 用户(1) + 助手(1) <br/> + 用户(2) + 助手(2) <br/> + 用户(3) + 助手(3) <br/> + **用户(4)** ◀ 缓存 | 系统到用户(3) 从缓存读取；<br/> 助手(3) + 用户(4) 写入缓存 |

缓存断点自动移动到每个请求中最后一个可缓存块，因此随着对话增长，您无需更新任何 `cache_control` 标记。

### TTL 支持

默认情况下，自动缓存使用 5 分钟 TTL。您可以指定 1 小时 TTL，价格为基础输入令牌价格的 2 倍：

```json
{ "cache_control": { "type": "ephemeral", "ttl": "1h" } }
```

### 与块级缓存结合

自动缓存与 [显式缓存断点](#explicit-cache-breakpoints) 兼容。一起使用时，自动缓存断点使用 4 个可用断点槽中的一个。

这让您可以结合两种方法。例如，使用显式断点独立缓存系统提示词和工具，同时自动缓存处理对话：

```json
{
  "model": "claude-opus-4-6",
  "max_tokens": 1024,
  "cache_control": { "type": "ephemeral" },
  "system": [
    {
      "type": "text",
      "text": "You are a helpful assistant.",
      "cache_control": { "type": "ephemeral" }
    }
  ],
  "messages": [{ "role": "user", "content": "What are the key terms?" }]
}
```

### 保持不变的内容

自动缓存使用相同的底层缓存基础设施。定价、最小令牌阈值、上下文排序要求和 20 块回溯窗口都与显式断点相同。

### 边界情况

- 如果最后一个块已经有一个具有相同 TTL 的显式 `cache_control`，自动缓存是无操作的。
- 如果最后一个块有一个具有不同 TTL 的显式 `cache_control`，API 返回 400 错误。
- 如果已经存在 4 个显式块级断点，API 返回 400 错误（没有自动缓存的槽位）。
- 如果最后一个块不符合自动缓存断点目标的条件，系统会静默向后遍历以找到最近的符合条件的块。如果找不到，缓存会被跳过。

<Note>
自动缓存在 Claude API 和 Azure AI Foundry（预览版）上可用。对 Amazon Bedrock 和 Google Vertex AI 的支持即将推出。
</Note>

---

## 显式缓存断点

为了更好地控制缓存，您可以直接在单个内容块上放置 `cache_control`。当您需要缓存以不同频率变化的不同部分，或需要对缓存内容进行细粒度控制时，这很有用。

### 构建您的提示词

将静态内容（工具定义、系统指令、上下文、示例）放在提示词的开头。使用 `cache_control` 参数标记可重用内容的结尾以进行缓存。

缓存前缀按以下顺序创建：`tools`、`system`，然后 `messages`。此顺序形成一个层次结构，其中每个级别都建立在前一个级别之上。

#### 自动前缀检查如何工作

您可以在静态内容的末尾使用单个缓存断点，系统会自动找到先前请求已写入缓存的最长前缀。理解这如何工作有助于您优化缓存策略。

**三个核心原则：**

1. **缓存写入仅在您的断点处发生。** 用 `cache_control` 标记块会写入恰好一个缓存条目：在该块处结束的前缀的哈希。系统不会为任何较早的位置写入条目。因为哈希是累积的，涵盖直到并包括断点的所有内容，改变断点处或之前的任何块会在下一个请求中产生不同的哈希。

2. **缓存读取向后查找先前请求写入的条目。** 在每个请求上，系统计算您的断点处的前缀哈希，并检查匹配的缓存条目。如果不存在，它逐块向后遍历，检查每个较早位置的前缀哈希是否与已在缓存中的内容匹配。它在寻找先前的写入，而不是稳定的内容。

3. **回溯窗口是 20 块。** 系统每个断点最多检查 20 个位置，将断点本身计为第一个。如果系统在该窗口内找不到匹配的条目，检查停止（或从下一个显式断点恢复，如果有的话）。

**示例：在不断增长的对话中回溯**

您在每个轮次追加新块，并在每个请求的最后一个块上设置 `cache_control`：

- **轮次 1：** 10 块，断点在块 10。不存在先前的缓存条目。系统在块 10 处写入条目。
- **轮次 2：** 15 块，断点在块 15。块 15 没有条目，所以系统向后遍历到块 10 并找到轮次 1 条目。块 10 处缓存命中；系统仅处理块 11 到 15 的新内容，并在块 15 处写入新条目。
- **轮次 3：** 35 块，断点在块 35。系统检查 20 个位置（块 35 到 16）并找不到任何内容。轮次 2 条目在块 15 处，位于窗口外一个位置，所以没有缓存命中。添加第二个断点在块 15 处启动第二个回溯窗口，该窗口找到轮次 2 条目。

**常见错误：断点在每个请求都变化的内容上**

您的提示词有一个大的静态系统上下文（块 1 到 5），后面是包含时间戳和用户消息的每个请求块（块 6）。您在块 6 上设置 `cache_control`：

- **请求 1：** 块 6 处缓存写入。哈希包括时间戳。
- **请求 2：** 时间戳不同，所以块 6 处的前缀哈希不同。回溯遍历块 5、4、3、2 和 1，但系统从未在这些位置中的任何一个写入条目。没有缓存命中。您在每个请求上支付新的缓存写入费用，永远不会获得读取。

回溯不会在您的断点后面找到稳定的内容并缓存它。它找到先前请求已写入的条目，而写入仅在断点处发生。将 `cache_control` 移到块 5，即跨请求保持相同的最后一个块，每个后续请求都会读取缓存的前缀。[自动缓存](#automatic-caching) 陷入同样的陷阱：它将断点放在最后一个可缓存块上，在这个结构中是每个请求都变化的块，所以改为使用块 5 上的显式断点。

**关键要点：** 将 `cache_control` 放在最后一个块上，其前缀在您想要共享缓存的请求中是相同的。在不断增长的对话中，只要每个轮次添加少于 20 块，最后一个块就可以工作：较早的内容永远不会改变，所以下一个请求的回溯找到先前的写入。对于具有变化后缀的提示词（时间戳、每个请求的上下文、传入消息），将断点放在静态前缀的末尾，而不是在变化块上。

#### 何时使用多个断点

如果您想要以下情况，可以定义最多 4 个缓存断点：
- 缓存以不同频率变化的不同部分（例如，工具很少变化，但上下文每天更新）
- 对缓存内容进行更多控制
- 确保缓存命中，当不断增长的对话将您的断点推过最后一次写入 20 个或更多块时

<Note>
**重要限制：** 回溯只能找到较早请求已写入的条目。如果不断增长的对话将您的断点推过最后一次写入 20 个或更多块，回溯窗口会错过它。从一开始就添加第二个断点更接近该位置，以便在您需要它之前在那里积累写入。
</Note>

### 理解缓存断点成本

**缓存断点本身不增加任何成本。** 您仅需为以下内容付费：
- **缓存写入**：当新内容写入缓存时（比基础输入令牌多 25%，用于 5 分钟 TTL）
- **缓存读取**：当使用缓存内容时（基础输入令牌价格的 10%）
- **常规输入令牌**：对于任何未缓存的内容

添加更多 `cache_control` 断点不会增加您的成本 - 您仍然根据实际缓存和读取的内容支付相同的金额。断点只是让您控制哪些部分可以独立缓存。

---

## 缓存策略和注意事项

### 缓存限制
最小可缓存提示词长度为：
- [Claude Mythos Preview](https://anthropic.com/glasswing)、Claude Opus 4.6 和 Claude Opus 4.5 为 4096 个令牌
- Claude Sonnet 4.6 为 2048 个令牌
- Claude Sonnet 4.5、Claude Opus 4.1、Claude Opus 4、Claude Sonnet 4 和 Claude Sonnet 3.7（[已弃用](/docs/zh-CN/about-claude/model-deprecations)）为 1024 个令牌
- Claude Haiku 4.5 为 4096 个令牌
- Claude Haiku 3.5（[已弃用](/docs/zh-CN/about-claude/model-deprecations)）和 Claude Haiku 3 为 2048 个令牌

较短的提示词无法缓存，即使用 `cache_control` 标记也是如此。任何缓存少于此数量令牌的请求都将在没有缓存的情况下处理，不会返回错误。要验证提示词是否被缓存，请检查响应使用 [字段](/docs/zh-CN/build-with-claude/prompt-caching#tracking-cache-performance)：如果 `cache_creation_input_tokens` 和 `cache_read_input_tokens` 都为 0，则提示词未被缓存（可能是因为它未达到最小长度要求）。

如果您的提示词略低于您使用的模型的最小值，扩展缓存内容以达到阈值通常是值得的。缓存读取的成本远低于未缓存的输入令牌，因此达到最小值可以降低频繁重用的提示词的成本。

对于并发请求，请注意缓存条目仅在第一个响应开始后才可用。如果您需要并行请求的缓存命中，请在发送后续请求之前等待第一个响应。

目前，"ephemeral"是唯一支持的缓存类型，默认生命周期为 5 分钟。

### 可以缓存的内容
请求中的大多数块都可以缓存。这包括：

- 工具：`tools` 数组中的工具定义
- 系统消息：`system` 数组中的内容块
- 文本消息：`messages.content` 数组中的内容块，用于用户和助手轮次
- 图像和文档：`messages.content` 数组中的内容块，在用户轮次中
- 工具使用和工具结果：`messages.content` 数组中的内容块，在用户和助手轮次中

这些元素中的每一个都可以缓存，可以自动缓存或通过用 `cache_control` 标记来缓存。

### 无法缓存的内容
虽然大多数请求块都可以缓存，但有一些例外：

- 思考块无法直接用 `cache_control` 缓存。但是，思考块可以与其他内容一起缓存，当它们出现在先前的助手轮次中时。以这种方式缓存时，它们在从缓存读取时确实计为输入令牌。
- 子内容块（如 [引用](/docs/zh-CN/build-with-claude/citations)）本身无法直接缓存。相反，缓存顶级块。

    在引用的情况下，作为引用源材料的顶级文档内容块可以缓存。这允许您通过缓存引用将引用的文档来有效地使用提示词缓存。
- 空文本块无法缓存。

### 什么使缓存失效

对缓存内容的修改可能会使部分或全部缓存失效。

如 [构建您的提示词](#structuring-your-prompt) 中所述，缓存遵循层次结构：`tools` → `system` → `messages`。每个级别的更改会使该级别及所有后续级别失效。

下表显示了不同类型的更改会使缓存的哪些部分失效。✘ 表示缓存失效，✓ 表示缓存保持有效。

| 变化内容 | 工具缓存 | 系统缓存 | 消息缓存 | 影响 |
|------------|------------------|---------------|----------------|-------------|
| **工具定义** | ✘ | ✘ | ✘ | 修改工具定义（名称、描述、参数）会使整个缓存失效 |
| **网络搜索切换** | ✓ | ✘ | ✘ | 启用/禁用网络搜索会修改系统提示词 |
| **引用切换** | ✓ | ✘ | ✘ | 启用/禁用引用会修改系统提示词 |
| **速度设置** | ✓ | ✘ | ✘ | 在 [`speed: "fast"` 和标准速度](/docs/zh-CN/build-with-claude/fast-mode) 之间切换会使系统和消息缓存失效 |
| **工具选择** | ✓ | ✓ | ✘ | 对 `tool_choice` 参数的更改仅影响消息块 |
| **图像** | ✓ | ✓ | ✘ | 在提示词中的任何位置添加/删除图像会影响消息块 |
| **思考参数** | ✓ | ✓ | ✘ | 对扩展思考设置的更改（启用/禁用、预算）会影响消息块 |
| **传递给扩展思考请求的非工具结果** | ✓ | ✓ | ✘ | 当在启用扩展思考的请求中传递非工具结果时，所有先前缓存的思考块都会从上下文中删除，任何跟随这些思考块的上下文中的消息都会从缓存中删除。有关更多详情，请参阅 [使用思考块缓存](#caching-with-thinking-blocks)。 |

### 跟踪缓存性能

使用这些 API 响应字段监控缓存性能，在响应中的 `usage` 内（或如果 [流式传输](/docs/zh-CN/build-with-claude/streaming) 则为 `message_start` 事件）：

- `cache_creation_input_tokens`：创建新条目时写入缓存的令牌数。
- `cache_read_input_tokens`：为此请求从缓存检索的令牌数。
- `input_tokens`：未从缓存读取或用于创建缓存的输入令牌数（即最后一个缓存断点之后的令牌）。

<Note>
**理解令牌分解**

`input_tokens` 字段仅表示在您的请求中最后一个缓存断点**之后**的令牌 - 不是您发送的所有输入令牌。

要计算总输入令牌：
```text
total_input_tokens = cache_read_input_tokens + cache_creation_input_tokens + input_tokens
```

**空间解释：**
- `cache_read_input_tokens` = 断点前已缓存的令牌（读取）
- `cache_creation_input_tokens` = 断点前现在被缓存的令牌（写入）
- `input_tokens` = 您最后一个断点之后的令牌（不符合缓存条件）

**示例：** 如果您有一个请求，其中有 100,000 个令牌的缓存内容（从缓存读取），0 个令牌的新内容被缓存，以及 50 个令牌在您的用户消息中（在缓存断点之后）：
- `cache_read_input_tokens`：100,000
- `cache_creation_input_tokens`：0
- `input_tokens`：50
- **处理的总输入令牌**：100,050 个令牌

这对于理解成本和速率限制都很重要，因为在有效使用缓存时，`input_tokens` 通常会比您的总输入小得多。
</Note>

### 使用思考块进行缓存

当使用[扩展思考](/docs/zh-CN/build-with-claude/extended-thinking)与提示缓存时，思考块有特殊的行为：

**与其他内容一起自动缓存**：虽然思考块不能用 `cache_control` 显式标记，但当您在后续 API 调用中传递工具结果时，它们会作为请求内容的一部分被缓存。这通常发生在工具使用期间，当您将思考块传回以继续对话时。

**输入令牌计数**：当思考块从缓存中读取时，它们在您的使用指标中计为输入令牌。这对于成本计算和令牌预算很重要。

**缓存失效模式**：
- 当仅提供工具结果作为用户消息时，缓存保持有效
- 当添加非工具结果用户内容时，缓存会失效，导致所有先前的思考块被删除
- 即使没有显式的 `cache_control` 标记，这种缓存行为也会发生

有关缓存失效的更多详情，请参阅[什么会使缓存失效](#what-invalidates-the-cache)。

**工具使用示例**：
```text
请求 1：用户："巴黎的天气怎样？"
响应：[thinking_block_1] + [tool_use block 1]

请求 2：
用户：["巴黎的天气怎样？"],
助手：[thinking_block_1] + [tool_use block 1],
用户：[tool_result_1, cache=True]
响应：[thinking_block_2] + [text block 2]
# 请求 2 缓存其请求内容（不是响应）
# 缓存包括：用户消息、thinking_block_1、tool_use block 1 和 tool_result_1

请求 3：
用户：["巴黎的天气怎样？"],
助手：[thinking_block_1] + [tool_use block 1],
用户：[tool_result_1, cache=True],
助手：[thinking_block_2] + [text block 2],
用户：[文本响应, cache=True]
# 非工具结果用户块会导致所有思考块被忽略
# 此请求的处理方式就像思考块从未存在过一样
```

当包含非工具结果用户块时，它指定了一个新的助手循环，所有先前的思考块都会从上下文中删除。

有关更详细的信息，请参阅[扩展思考文档](/docs/zh-CN/build-with-claude/extended-thinking#understanding-thinking-block-caching-behavior)。

### 缓存存储和共享

<Warning>
从 2026 年 2 月 5 日开始，提示缓存将使用工作区级隔离而不是组织级隔离。缓存将按工作区隔离，确保同一组织内工作区之间的数据分离。此更改适用于 Claude API 和 Azure AI Foundry（预览版）；Amazon Bedrock 和 Google Vertex AI 将保持组织级缓存隔离。如果您使用多个工作区，请审查您的缓存策略以考虑此更改。
</Warning>

- **组织隔离**：缓存在组织之间隔离。不同的组织永远不会共享缓存，即使他们使用相同的提示。

- **精确匹配**：缓存命中需要 100% 相同的提示段，包括所有文本和图像，直到并包括用缓存控制标记的块。

- **输出令牌生成**：提示缓存对输出令牌生成没有影响。您收到的响应将与不使用提示缓存时收到的响应相同。

### 有效缓存的最佳实践

要优化提示缓存性能：

- 从[自动缓存](#automatic-caching)开始进行多轮对话。它会自动处理断点管理。
- 当您需要缓存具有不同更改频率的不同部分时，使用[显式块级断点](#explicit-cache-breakpoints)。
- 缓存稳定、可重用的内容，如系统指令、背景信息、大型上下文或频繁的工具定义。
- 将缓存内容放在提示的开头以获得最佳性能。
- 策略性地使用缓存断点来分离不同的可缓存前缀部分。
- 将断点放在跨请求保持相同的最后一个块上。对于具有静态前缀和变化后缀（时间戳、每个请求的上下文、传入消息）的提示，这是前缀的末尾，而不是变化块。
- 定期分析缓存命中率并根据需要调整您的策略。

### 针对不同用例进行优化

根据您的场景定制您的提示缓存策略：

- 对话代理：降低成本并减少扩展对话的延迟，特别是那些具有长指令或上传文档的对话。
- 编码助手：通过在提示中保留相关部分或代码库的摘要版本来改进自动完成和代码库问答。
- 大型文档处理：将完整的长篇材料（包括图像）合并到您的提示中，而不增加响应延迟。
- 详细指令集：共享广泛的指令、程序和示例列表，以微调 Claude 的响应。开发人员通常在提示中包含一两个示例，但使用提示缓存，您可以通过包含 20 多个高质量答案的多样化示例来获得更好的性能。
- 代理工具使用：增强涉及多个工具调用和迭代代码更改的场景的性能，其中每一步通常需要新的 API 调用。
- 与书籍、论文、文档、播客转录和其他长篇内容交谈：通过将整个文档嵌入到提示中，让用户向其提问，使任何知识库活跃起来。

### 排查常见问题

如果遇到意外行为：

- 确保缓存的部分在调用中相同。对于显式断点，验证 `cache_control` 标记位于相同位置
- 检查调用是否在缓存生命周期内进行（默认为 5 分钟）
- 验证 `tool_choice` 和图像使用在调用之间保持一致
- 验证您正在缓存至少最少数量的令牌以用于您使用的模型（请参阅[缓存限制](#cache-limitations)）。基于长度的缓存失败是无声的：请求成功，但 `cache_creation_input_tokens` 和 `cache_read_input_tokens` 都将为 0
- 确认您的断点位于跨请求保持相同的块上。缓存写入仅在断点处发生，如果该块更改（时间戳、每个请求的上下文、传入消息），前缀哈希永远不会匹配。回溯不会在断点后面找到稳定内容；它只会找到早期请求在其自己的断点处写入的条目
- 验证您的 `tool_use` 内容块中的键具有稳定的顺序，因为某些语言（例如 Swift、Go）在 JSON 转换期间随机化键顺序，破坏缓存

<Note>
对 `tool_choice` 的更改或提示中任何位置的图像的存在/不存在将使缓存失效，需要创建新的缓存条目。有关缓存失效的更多详情，请参阅[什么会使缓存失效](#what-invalidates-the-cache)。
</Note>

---
## 1 小时缓存持续时间

如果您发现 5 分钟太短，Anthropic 还提供 1 小时缓存持续时间[需额外付费](#pricing)。

要使用扩展缓存，请在 `cache_control` 定义中包含 `ttl`，如下所示：
```json hidelines={1,-1}
{
  "cache_control": {
    "type": "ephemeral",
    "ttl": "1h"
  }
}
```

响应将包含详细的缓存信息，如下所示：
```json Output
{
  "usage": {
    "input_tokens": 2048,
    "cache_read_input_tokens": 1800,
    "cache_creation_input_tokens": 248,
    "output_tokens": 503,

    "cache_creation": {
      "ephemeral_5m_input_tokens": 456,
      "ephemeral_1h_input_tokens": 100
    }
  }
}
```

请注意，当前的 `cache_creation_input_tokens` 字段等于 `cache_creation` 对象中值的总和。

### 何时使用 1 小时缓存

如果您有定期使用的提示（即每 5 分钟以上使用一次的系统提示），请继续使用 5 分钟缓存，因为这将继续以无额外费用的方式刷新。

1 小时缓存最适合用于以下场景：
- 当您有可能使用频率少于 5 分钟但多于每小时一次的提示时。例如，当代理端代理需要超过 5 分钟时，或者当存储与用户的长聊天对话时，您通常预期该用户可能在接下来的 5 分钟内不会响应。
- 当延迟很重要且您的后续提示可能在 5 分钟后发送时。
- 当您想改进您的速率限制利用率时，因为缓存命中不会从您的速率限制中扣除。

<Note>
5 分钟和 1 小时缓存在延迟方面的行为相同。对于长文档，您通常会看到改进的首令牌时间。
</Note>

### 混合不同的 TTL

您可以在同一请求中使用 1 小时和 5 分钟缓存控制，但有一个重要的限制：具有较长 TTL 的缓存条目必须出现在较短 TTL 之前（即 1 小时缓存条目必须出现在任何 5 分钟缓存条目之前）。

混合 TTL 时，API 在您的提示中确定三个计费位置：
1. 位置 `A`：最高缓存命中处的令牌计数（如果没有命中则为 0）。
2. 位置 `B`：`A` 之后最高 1 小时 `cache_control` 块处的令牌计数（如果不存在则等于 `A`）。
3. 位置 `C`：最后一个 `cache_control` 块处的令牌计数。

<Note>
如果 `B` 和/或 `C` 大于 `A`，它们必然是缓存未命中，因为 `A` 是最高缓存命中。
</Note>

您将被收费：
1. `A` 的缓存读取令牌。
2. `(B - A)` 的 1 小时缓存写入令牌。
3. `(C - B)` 的 5 分钟缓存写入令牌。

以下是 3 个示例。这描绘了 3 个请求的输入令牌，每个请求都有不同的缓存命中和缓存未命中。每个都有不同的计算定价，如彩色框所示。
![混合 TTL 图表](/docs/images/prompt-cache-mixed-ttl.svg)

---

## 提示词缓存示例

为了帮助您开始使用提示词缓存，[提示词缓存cookbook](https://platform.claude.com/cookbook/misc-prompt-caching)提供了详细的示例和最佳实践。

以下代码片段展示了各种提示词缓存模式。这些示例演示了如何在不同场景中实现缓存，帮助您理解此功能的实际应用：

<section title="大型上下文缓存示例">

<CodeGroup>
```bash Shell
curl https://api.anthropic.com/v1/messages \
     --header "x-api-key: $ANTHROPIC_API_KEY" \
     --header "anthropic-version: 2023-06-01" \
     --header "content-type: application/json" \
     --data \
'{
    "model": "claude-opus-4-6",
    "max_tokens": 1024,
    "system": [
        {
            "type": "text",
            "text": "You are an AI assistant tasked with analyzing legal documents."
        },
        {
            "type": "text",
            "text": "Here is the full text of a complex legal agreement: [Insert full text of a 50-page legal agreement here]",
            "cache_control": {"type": "ephemeral"}
        }
    ],
    "messages": [
        {
            "role": "user",
            "content": "What are the key terms and conditions in this agreement?"
        }
    ]
}'
```

```bash CLI
ant messages create <<'YAML'
model: claude-opus-4-6
max_tokens: 1024
system:
  - type: text
    text: You are an AI assistant tasked with analyzing legal documents.
  - type: text
    text: >-
      Here is the full text of a complex legal agreement:
      [Insert full text of a 50-page legal agreement here]
    cache_control:
      type: ephemeral
messages:
  - role: user
    content: What are the key terms and conditions in this agreement?
YAML
```

```python Python hidelines={1..2}
import anthropic

client = anthropic.Anthropic()

response = client.messages.create(
    model="claude-opus-4-6",
    max_tokens=1024,
    system=[
        {
            "type": "text",
            "text": "You are an AI assistant tasked with analyzing legal documents.",
        },
        {
            "type": "text",
            "text": "Here is the full text of a complex legal agreement: [Insert full text of a 50-page legal agreement here]",
            "cache_control": {"type": "ephemeral"},
        },
    ],
    messages=[
        {
            "role": "user",
            "content": "What are the key terms and conditions in this agreement?",
        }
    ],
)
print(response.model_dump_json())
```

```typescript TypeScript hidelines={1..2}
import Anthropic from "@anthropic-ai/sdk";

const client = new Anthropic();

const response = await client.messages.create({
  model: "claude-opus-4-6",
  max_tokens: 1024,
  system: [
    {
      type: "text",
      text: "You are an AI assistant tasked with analyzing legal documents."
    },
    {
      type: "text",
      text: "Here is the full text of a complex legal agreement: [Insert full text of a 50-page legal agreement here]",
      cache_control: { type: "ephemeral" }
    }
  ],
  messages: [
    {
      role: "user",
      content: "What are the key terms and conditions in this agreement?"
    }
  ]
});
console.log(response);
```

```csharp C# hidelines={1..10,-2..}
using System;
using System.Threading.Tasks;
using System.Collections.Generic;
using Anthropic;
using Anthropic.Models.Messages;

public class Program
{
    public static async Task Main(string[] args)
    {
        AnthropicClient client = new()
        {
            ApiKey = Environment.GetEnvironmentVariable("ANTHROPIC_API_KEY")
        };

        var parameters = new MessageCreateParams
        {
            Model = Model.ClaudeOpus4_6,
            MaxTokens = 1024,
            System = new MessageCreateParamsSystem(new List<TextBlockParam>
            {
                new TextBlockParam()
                {
                    Text = "You are an AI assistant tasked with analyzing legal documents.",
                },
                new TextBlockParam()
                {
                    Text = "Here is the full text of a complex legal agreement: [Insert full text of a 50-page legal agreement here]",
                    CacheControl = new CacheControlEphemeral(),
                },
            }),
            Messages =
            [
                new()
                {
                    Role = Role.User,
                    Content = "What are the key terms and conditions in this agreement?"
                }
            ]
        };

        var message = await client.Messages.Create(parameters);
        Console.WriteLine(message);
    }
}
```

```go Go hidelines={1..11,-1}
package main

import (
	"context"
	"fmt"
	"log"

	"github.com/anthropics/anthropic-sdk-go"
)

func main() {
	client := anthropic.NewClient()

	response, err := client.Messages.New(context.TODO(), anthropic.MessageNewParams{
		Model:     anthropic.ModelClaudeOpus4_6,
		MaxTokens: 1024,
		System: []anthropic.TextBlockParam{
			{
				Text: "You are an AI assistant tasked with analyzing legal documents.",
			},
			{
				Text:         "Here is the full text of a complex legal agreement: [Insert full text of a 50-page legal agreement here]",
				CacheControl: anthropic.NewCacheControlEphemeralParam(),
			},
		},
		Messages: []anthropic.MessageParam{
			anthropic.NewUserMessage(anthropic.NewTextBlock("What are the key terms and conditions in this agreement?")),
		},
	})
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("%+v\n", response)
}
```

```java Java hidelines={1..2,4..12,-2..}
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlockParam;
import java.util.List;

public class LegalDocumentAnalysisExample {

  public static void main(String[] args) {
    AnthropicClient client = AnthropicOkHttpClient.fromEnv();

    MessageCreateParams params = MessageCreateParams.builder()
      .model(Model.CLAUDE_OPUS_4_6)
      .maxTokens(1024)
      .systemOfTextBlockParams(
        List.of(
          TextBlockParam.builder()
            .text("You are an AI assistant tasked with analyzing legal documents.")
            .build(),
          TextBlockParam.builder()
            .text(
              "Here is the full text of a complex legal agreement: [Insert full text of a 50-page legal agreement here]"
            )
            .cacheControl(CacheControlEphemeral.builder().build())
            .build()
        )
      )
      .addUserMessage("What are the key terms and conditions in this agreement?")
      .build();

    Message message = client.messages().create(params);
    System.out.println(message);
  }
}
```

```php PHP hidelines={1..4}
<?php

use Anthropic\Client;

$client = new Client(apiKey: getenv("ANTHROPIC_API_KEY"));

$message = $client->messages->create(
    maxTokens: 1024,
    messages: [
        [
            'role' => 'user',
            'content' => 'What are the key terms and conditions in this agreement?'
        ]
    ],
    model: 'claude-opus-4-6',
    system: [
        [
            'type' => 'text',
            'text' => 'You are an AI assistant tasked with analyzing legal documents.'
        ],
        [
            'type' => 'text',
            'text' => 'Here is the full text of a complex legal agreement: [Insert full text of a 50-page legal agreement here]',
            'cache_control' => ['type' => 'ephemeral']
        ]
    ],
);

echo $message->content[0]->text;
```

```ruby Ruby nocheck hidelines={1..2}
require "anthropic"

client = Anthropic::Client.new

message = client.messages.create(
  model: "claude-opus-4-6",
  max_tokens: 1024,
  system: [
    {
      type: "text",
      text: "You are an AI assistant tasked with analyzing legal documents."
    },
    {
      type: "text",
      text: "Here is the full text of a complex legal agreement: [Insert full text of a 50-page legal agreement here]",
      cache_control: { type: "ephemeral" }
    }
  ],
  messages: [
    {
      role: "user",
      content: "What are the key terms and conditions in this agreement?"
    }
  ]
)
puts message
```
</CodeGroup>
此示例演示了基本的提示词缓存用法，将法律协议的完整文本缓存为前缀，同时保持用户指令未缓存。

对于第一个请求：
- `input_tokens`：仅用户消息中的令牌数
- `cache_creation_input_tokens`：整个系统消息中的令牌数，包括法律文档
- `cache_read_input_tokens`：0（第一个请求时没有缓存命中）

对于缓存生命周期内的后续请求：
- `input_tokens`：仅用户消息中的令牌数
- `cache_creation_input_tokens`：0（无新的缓存创建）
- `cache_read_input_tokens`：整个缓存系统消息中的令牌数

</section>

<section title="缓存工具定义">

工具定义可以通过在`tools`数组中的最后一个工具上放置`cache_control`来缓存。在该工具之前和包括该工具的所有工具都被缓存为单个前缀。

```json
{
  "model": "claude-opus-4-6",
  "max_tokens": 1024,
  "tools": [
    {
      "name": "get_weather",
      "description": "Get the current weather in a given location",
      "input_schema": {
        "type": "object",
        "properties": { "location": { "type": "string" } },
        "required": ["location"]
      }
    },
    {
      "name": "get_time",
      "description": "Get the current time in a given time zone",
      "input_schema": {
        "type": "object",
        "properties": { "timezone": { "type": "string" } },
        "required": ["timezone"]
      },
      "cache_control": { "type": "ephemeral" }
    }
  ],
  "messages": [{ "role": "user", "content": "What is the weather and time in New York?" }]
}
```

在第一个请求中，`cache_creation_input_tokens`反映所有工具定义的令牌计数。在缓存生命周期内的后续请求中，这些令牌改为出现在`cache_read_input_tokens`下。

有关工具定义、`defer_loading`和缓存失效之间的详细交互，请参阅[使用提示词缓存的工具使用](/docs/zh-CN/agents-and-tools/tool-use/tool-use-with-prompt-caching)。

</section>

<section title="继续多轮对话">

<CodeGroup>

```bash Shell
curl https://api.anthropic.com/v1/messages \
     --header "x-api-key: $ANTHROPIC_API_KEY" \
     --header "anthropic-version: 2023-06-01" \
     --header "content-type: application/json" \
     --data \
'{
    "model": "claude-opus-4-6",
    "max_tokens": 1024,
    "system": [
        {
            "type": "text",
            "text": "...long system prompt",
            "cache_control": {"type": "ephemeral"}
        }
    ],
    "messages": [
        {
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": "Hello, can you tell me more about the solar system?"
                }
            ]
        },
        {
            "role": "assistant",
            "content": "Certainly! The solar system is the collection of celestial bodies that orbit our Sun. It consists of eight planets, numerous moons, asteroids, comets, and other objects. The planets, in order from closest to farthest from the Sun, are: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune. Each planet has its own unique characteristics and features. Is there a specific aspect of the solar system you would like to know more about?"
        },
        {
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": "Good to know."
                },
                {
                    "type": "text",
                    "text": "Tell me more about Mars.",
                    "cache_control": {"type": "ephemeral"}
                }
            ]
        }
    ]
}'
```

```bash CLI
ant messages create <<'YAML'
model: claude-opus-4-6
max_tokens: 1024
system:
  - type: text
    text: "...long system prompt"
    cache_control:
      type: ephemeral
messages:
  - role: user
    content:
      - type: text
        text: Hello, can you tell me more about the solar system?
  - role: assistant
    content: >-
      Certainly! The solar system is the collection of celestial bodies that
      orbit our Sun. It consists of eight planets, numerous moons, asteroids,
      comets, and other objects. The planets, in order from closest to farthest
      from the Sun, are: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus,
      and Neptune. Each planet has its own unique characteristics and features.
      Is there a specific aspect of the solar system you would like to know
      more about?
  - role: user
    content:
      - type: text
        text: Good to know.
      - type: text
        text: Tell me more about Mars.
        cache_control:
          type: ephemeral
YAML
```

```python Python hidelines={1..2}
import anthropic

client = anthropic.Anthropic()

response = client.messages.create(
    model="claude-opus-4-6",
    max_tokens=1024,
    system=[
        {
            "type": "text",
            "text": "...long system prompt",
            "cache_control": {"type": "ephemeral"},
        }
    ],
    messages=[
        # ...long conversation so far
        {
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": "Hello, can you tell me more about the solar system?",
                }
            ],
        },
        {
            "role": "assistant",
            "content": "Certainly! The solar system is the collection of celestial bodies that orbit our Sun. It consists of eight planets, numerous moons, asteroids, comets, and other objects. The planets, in order from closest to farthest from the Sun, are: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune. Each planet has its own unique characteristics and features. Is there a specific aspect of the solar system you'd like to know more about?",
        },
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "Good to know."},
                {
                    "type": "text",
                    "text": "Tell me more about Mars.",
                    "cache_control": {"type": "ephemeral"},
                },
            ],
        },
    ],
)
print(response.model_dump_json())
```

```typescript TypeScript hidelines={1..2}
import Anthropic from "@anthropic-ai/sdk";

const client = new Anthropic();

const response = await client.messages.create({
  model: "claude-opus-4-6",
  max_tokens: 1024,
  system: [
    {
      type: "text",
      text: "...long system prompt",
      cache_control: { type: "ephemeral" }
    }
  ],
  messages: [
    // ...long conversation so far
    {
      role: "user",
      content: [
        {
          type: "text",
          text: "Hello, can you tell me more about the solar system?"
        }
      ]
    },
    {
      role: "assistant",
      content:
        "Certainly! The solar system is the collection of celestial bodies that orbit our Sun. It consists of eight planets, numerous moons, asteroids, comets, and other objects. The planets, in order from closest to farthest from the Sun, are: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune. Each planet has its own unique characteristics and features. Is there a specific aspect of the solar system you'd like to know more about?"
    },
    {
      role: "user",
      content: [
        {
          type: "text",
          text: "Good to know."
        },
        {
          type: "text",
          text: "Tell me more about Mars.",
          cache_control: { type: "ephemeral" }
        }
      ]
    }
  ]
});
console.log(response);
```

```csharp C# hidelines={1..6}
using Anthropic;
using Anthropic.Models.Messages;
using System.Collections.Generic;

AnthropicClient client = new();

var parameters = new MessageCreateParams
{
    Model = Model.ClaudeOpus4_6,
    MaxTokens = 1024,
    System = new MessageCreateParamsSystem(new List<TextBlockParam>
    {
        new TextBlockParam()
        {
            Text = "...long system prompt",
            CacheControl = new CacheControlEphemeral(),
        },
    }),
    Messages =
    [
        new()
        {
            Role = Role.User,
            Content = new MessageParamContent(new List<ContentBlockParam>
            {
                new ContentBlockParam(new TextBlockParam("Hello, can you tell me more about the solar system?")),
            }),
        },
        new()
        {
            Role = Role.Assistant,
            Content = "Certainly! The solar system is the collection of celestial bodies that orbit our Sun. It consists of eight planets, numerous moons, asteroids, comets, and other objects. The planets, in order from closest to farthest from the Sun, are: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune. Each planet has its own unique characteristics and features. Is there a specific aspect of the solar system you would like to know more about?"
        },
        new()
        {
            Role = Role.User,
            Content = new MessageParamContent(new List<ContentBlockParam>
            {
                new ContentBlockParam(new TextBlockParam("Good to know.")),
                new ContentBlockParam(new TextBlockParam()
                {
                    Text = "Tell me more about Mars.",
                    CacheControl = new CacheControlEphemeral(),
                }),
            })
        }
    ]
};

var message = await client.Messages.Create(parameters);
Console.WriteLine(message);
```

```go Go hidelines={1..11,-1}
package main

import (
	"context"
	"fmt"
	"log"

	"github.com/anthropics/anthropic-sdk-go"
)

func main() {
	client := anthropic.NewClient()

	response, err := client.Messages.New(context.TODO(), anthropic.MessageNewParams{
		Model:     anthropic.ModelClaudeOpus4_6,
		MaxTokens: 1024,
		System: []anthropic.TextBlockParam{
			{
				Text:         "...long system prompt",
				CacheControl: anthropic.NewCacheControlEphemeralParam(),
			},
		},
		Messages: []anthropic.MessageParam{
			anthropic.NewUserMessage(anthropic.NewTextBlock("Hello, can you tell me more about the solar system?")),
			anthropic.NewAssistantMessage(anthropic.NewTextBlock("Certainly! The solar system is the collection of celestial bodies that orbit our Sun. It consists of eight planets, numerous moons, asteroids, comets, and other objects. The planets, in order from closest to farthest from the Sun, are: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune. Each planet has its own unique characteristics and features. Is there a specific aspect of the solar system you would like to know more about?")),
			{
				Role: anthropic.MessageParamRoleUser,
				Content: []anthropic.ContentBlockParamUnion{
					anthropic.NewTextBlock("Good to know."),
					{OfText: &anthropic.TextBlockParam{
						Text:         "Tell me more about Mars.",
						CacheControl: anthropic.NewCacheControlEphemeralParam(),
					}},
				},
			},
		},
	})
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(response)
}
```

```java Java hidelines={1..2,4..13,-2..}
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlockParam;
import java.util.List;

public class ConversationWithCacheControlExample {

  public static void main(String[] args) {
    AnthropicClient client = AnthropicOkHttpClient.fromEnv();

    // Create ephemeral system prompt
    TextBlockParam systemPrompt = TextBlockParam.builder()
      .text("...long system prompt")
      .cacheControl(CacheControlEphemeral.builder().build())
      .build();

    // Create message params
    MessageCreateParams params = MessageCreateParams.builder()
      .model(Model.CLAUDE_OPUS_4_6)
      .maxTokens(1024)
      .systemOfTextBlockParams(List.of(systemPrompt))
      // First user message (without cache control)
      .addUserMessage("Hello, can you tell me more about the solar system?")
      // Assistant response
      .addAssistantMessage(
        "Certainly! The solar system is the collection of celestial bodies that orbit our Sun. It consists of eight planets, numerous moons, asteroids, comets, and other objects. The planets, in order from closest to farthest from the Sun, are: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune. Each planet has its own unique characteristics and features. Is there a specific aspect of the solar system you would like to know more about?"
      )
      // Second user message (with cache control)
      .addUserMessageOfBlockParams(
        List.of(
          ContentBlockParam.ofText(TextBlockParam.builder().text("Good to know.").build()),
          ContentBlockParam.ofText(
            TextBlockParam.builder()
              .text("Tell me more about Mars.")
              .cacheControl(CacheControlEphemeral.builder().build())
              .build()
          )
        )
      )
      .build();

    Message message = client.messages().create(params);
    System.out.println(message);
  }
}
```

```php PHP hidelines={1..4}
<?php

use Anthropic\Client;

$client = new Client(apiKey: getenv("ANTHROPIC_API_KEY"));

$message = $client->messages->create(
    maxTokens: 1024,
    messages: [
        [
            'role' => 'user',
            'content' => [
                [
                    'type' => 'text',
                    'text' => 'Hello, can you tell me more about the solar system?'
                ]
            ]
        ],
        [
            'role' => 'assistant',
            'content' => "Certainly! The solar system is the collection of celestial bodies that orbit our Sun. It consists of eight planets, numerous moons, asteroids, comets, and other objects. The planets, in order from closest to farthest from the Sun, are: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune. Each planet has its own unique characteristics and features. Is there a specific aspect of the solar system you would like to know more about?"
        ],
        [
            'role' => 'user',
            'content' => [
                ['type' => 'text', 'text' => 'Good to know.'],
                [
                    'type' => 'text',
                    'text' => 'Tell me more about Mars.',
                    'cache_control' => ['type' => 'ephemeral']
                ]
            ]
        ]
    ],
    model: 'claude-opus-4-6',
    system: [
        [
            'type' => 'text',
            'text' => '...long system prompt',
            'cache_control' => ['type' => 'ephemeral']
        ]
    ],
);

echo $message->content[0]->text;
```

```ruby Ruby nocheck hidelines={1..2}
require "anthropic"

client = Anthropic::Client.new

message = client.messages.create(
  model: "claude-opus-4-6",
  max_tokens: 1024,
  system: [
    {
      type: "text",
      text: "...long system prompt",
      cache_control: { type: "ephemeral" }
    }
  ],
  messages: [
    {
      role: "user",
      content: [
        {
          type: "text",
          text: "Hello, can you tell me more about the solar system?"
        }
      ]
    },
    {
      role: "assistant",
      content: "Certainly! The solar system is the collection of celestial bodies that orbit our Sun. It consists of eight planets, numerous moons, asteroids, comets, and other objects. The planets, in order from closest to farthest from the Sun, are: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune. Each planet has its own unique characteristics and features. Is there a specific aspect of the solar system you would like to know more about?"
    },
    {
      role: "user",
      content: [
        { type: "text", text: "Good to know." },
        {
          type: "text",
          text: "Tell me more about Mars.",
          cache_control: { type: "ephemeral" }
        }
      ]
    }
  ]
)
puts message
```
</CodeGroup>

此示例演示了如何在多轮对话中使用提示词缓存。

在每个轮次中，最后一条消息的最后一个块被标记为`cache_control`，以便对话可以逐步缓存。系统将自动查找并使用最长的先前缓存的块序列用于后续消息。也就是说，之前标记为`cache_control`块的块稍后不标记此项，但如果在5分钟内命中，它们仍将被视为缓存命中（也是缓存刷新！）。

此外，请注意`cache_control`参数放在系统消息上。这是为了确保如果它从缓存中被逐出（在5分钟以上未使用后），它将在下一个请求时被添加回缓存。

这种方法对于在进行中的对话中维护上下文而无需重复处理相同信息很有用。

当正确设置此项时，您应该在每个请求的使用响应中看到以下内容：
- `input_tokens`：新用户消息中的令牌数（将是最小的）
- `cache_creation_input_tokens`：新的助手和用户轮次中的令牌数
- `cache_read_input_tokens`：对话中直到上一轮的令牌数

</section>

<section title="综合示例：多个缓存断点">

<CodeGroup>

```bash Shell
curl https://api.anthropic.com/v1/messages \
     --header "x-api-key: $ANTHROPIC_API_KEY" \
     --header "anthropic-version: 2023-06-01" \
     --header "content-type: application/json" \
     --data \
'{
    "model": "claude-opus-4-6",
    "max_tokens": 1024,
    "tools": [
        {
            "name": "search_documents",
            "description": "Search through the knowledge base",
            "input_schema": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Search query"
                    }
                },
                "required": ["query"]
            }
        },
        {
            "name": "get_document",
            "description": "Retrieve a specific document by ID",
            "input_schema": {
                "type": "object",
                "properties": {
                    "doc_id": {
                        "type": "string",
                        "description": "Document ID"
                    }
                },
                "required": ["doc_id"]
            },
            "cache_control": {"type": "ephemeral"}
        }
    ],
    "system": [
        {
            "type": "text",
            "text": "You are a helpful research assistant with access to a document knowledge base.\n\n# Instructions\n- Always search for relevant documents before answering\n- Provide citations for your sources\n- Be objective and accurate in your responses\n- If multiple documents contain relevant information, synthesize them\n- Acknowledge when information is not available in the knowledge base",
            "cache_control": {"type": "ephemeral"}
        },
        {
            "type": "text",
            "text": "# Knowledge Base Context\n\nHere are the relevant documents for this conversation:\n\n## Document 1: Solar System Overview\nThe solar system consists of the Sun and all objects that orbit it...\n\n## Document 2: Planetary Characteristics\nEach planet has unique features. Mercury is the smallest planet...\n\n## Document 3: Mars Exploration\nMars has been a target of exploration for decades...\n\n[Additional documents...]",
            "cache_control": {"type": "ephemeral"}
        }
    ],
    "messages": [
        {
            "role": "user",
            "content": "Can you search for information about Mars rovers?"
        },
        {
            "role": "assistant",
            "content": [
                {
                    "type": "tool_use",
                    "id": "tool_1",
                    "name": "search_documents",
                    "input": {"query": "Mars rovers"}
                }
            ]
        },
        {
            "role": "user",
            "content": [
                {
                    "type": "tool_result",
                    "tool_use_id": "tool_1",
                    "content": "Found 3 relevant documents: Document 3 (Mars Exploration), Document 7 (Rover Technology), Document 9 (Mission History)"
                }
            ]
        },
        {
            "role": "assistant",
            "content": [
                {
                    "type": "text",
                    "text": "I found 3 relevant documents about Mars rovers. Let me get more details from the Mars Exploration document."
                }
            ]
        },
        {
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": "Yes, please tell me about the Perseverance rover specifically.",
                    "cache_control": {"type": "ephemeral"}
                }
            ]
        }
    ]
}'
```

```bash CLI
ant messages create <<'YAML'
model: claude-opus-4-6
max_tokens: 1024
tools:
  - name: search_documents
    description: Search through the knowledge base
    input_schema:
      type: object
      properties:
        query:
          type: string
          description: Search query
      required: [query]
  - name: get_document
    description: Retrieve a specific document by ID
    input_schema:
      type: object
      properties:
        doc_id:
          type: string
          description: Document ID
      required: [doc_id]
    cache_control:
      type: ephemeral
system:
  - type: text
    text: |-
      You are a helpful research assistant with access to a document knowledge base.

      # Instructions
      - Always search for relevant documents before answering
      - Provide citations for your sources
      - Be objective and accurate in your responses
      - If multiple documents contain relevant information, synthesize them
      - Acknowledge when information is not available in the knowledge base
    cache_control:
      type: ephemeral
  - type: text
    text: |-
      # Knowledge Base Context

      Here are the relevant documents for this conversation:

      ## Document 1: Solar System Overview
      The solar system consists of the Sun and all objects that orbit it...

      ## Document 2: Planetary Characteristics
      Each planet has unique features. Mercury is the smallest planet...

      ## Document 3: Mars Exploration
      Mars has been a target of exploration for decades...

      [Additional documents...]
    cache_control:
      type: ephemeral
messages:
  - role: user
    content: Can you search for information about Mars rovers?
  - role: assistant
    content:
      - type: tool_use
        id: tool_1
        name: search_documents
        input:
          query: Mars rovers
  - role: user
    content:
      - type: tool_result
        tool_use_id: tool_1
        content: >-
          Found 3 relevant documents: Document 3 (Mars Exploration),
          Document 7 (Rover Technology), Document 9 (Mission History)
  - role: assistant
    content:
      - type: text
        text: >-
          I found 3 relevant documents about Mars rovers. Let me get more
          details from the Mars Exploration document.
  - role: user
    content:
      - type: text
        text: Yes, please tell me about the Perseverance rover specifically.
        cache_control:
          type: ephemeral
YAML
```

```python Python hidelines={1..2}
import anthropic

client = anthropic.Anthropic()

response = client.messages.create(
    model="claude-opus-4-6",
    max_tokens=1024,
    tools=[
        {
            "name": "search_documents",
            "description": "Search through the knowledge base",
            "input_schema": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "Search query"}
                },
                "required": ["query"],
            },
        },
        {
            "name": "get_document",
            "description": "Retrieve a specific document by ID",
            "input_schema": {
                "type": "object",
                "properties": {
                    "doc_id": {"type": "string", "description": "Document ID"}
                },
                "required": ["doc_id"],
            },
            "cache_control": {"type": "ephemeral"},
        },
    ],
    system=[
        {
            "type": "text",
            "text": "You are a helpful research assistant with access to a document knowledge base.\n\n# Instructions\n- Always search for relevant documents before answering\n- Provide citations for your sources\n- Be objective and accurate in your responses\n- If multiple documents contain relevant information, synthesize them\n- Acknowledge when information is not available in the knowledge base",
            "cache_control": {"type": "ephemeral"},
        },
        {
            "type": "text",
            "text": "# Knowledge Base Context\n\nHere are the relevant documents for this conversation:\n\n## Document 1: Solar System Overview\nThe solar system consists of the Sun and all objects that orbit it...\n\n## Document 2: Planetary Characteristics\nEach planet has unique features. Mercury is the smallest planet...\n\n## Document 3: Mars Exploration\nMars has been a target of exploration for decades...\n\n[Additional documents...]",
            "cache_control": {"type": "ephemeral"},
        },
    ],
    messages=[
        {
            "role": "user",
            "content": "Can you search for information about Mars rovers?",
        },
        {
            "role": "assistant",
            "content": [
                {
                    "type": "tool_use",
                    "id": "tool_1",
                    "name": "search_documents",
                    "input": {"query": "Mars rovers"},
                }
            ],
        },
        {
            "role": "user",
            "content": [
                {
                    "type": "tool_result",
                    "tool_use_id": "tool_1",
                    "content": "Found 3 relevant documents: Document 3 (Mars Exploration), Document 7 (Rover Technology), Document 9 (Mission History)",
                }
            ],
        },
        {
            "role": "assistant",
            "content": [
                {
                    "type": "text",
                    "text": "I found 3 relevant documents about Mars rovers. Let me get more details from the Mars Exploration document.",
                }
            ],
        },
        {
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": "Yes, please tell me about the Perseverance rover specifically.",
                    "cache_control": {"type": "ephemeral"},
                }
            ],
        },
    ],
)
print(response.model_dump_json())
```

```typescript TypeScript hidelines={1..2}
import Anthropic from "@anthropic-ai/sdk";

const client = new Anthropic();

const response = await client.messages.create({
  model: "claude-opus-4-6",
  max_tokens: 1024,
  tools: [
    {
      name: "search_documents",
      description: "Search through the knowledge base",
      input_schema: {
        type: "object",
        properties: {
          query: {
            type: "string",
            description: "Search query"
          }
        },
        required: ["query"]
      }
    },
    {
      name: "get_document",
      description: "Retrieve a specific document by ID",
      input_schema: {
        type: "object",
        properties: {
          doc_id: {
            type: "string",
            description: "Document ID"
          }
        },
        required: ["doc_id"]
      },
      cache_control: { type: "ephemeral" }
    }
  ],
  system: [
    {
      type: "text",
      text: "You are a helpful research assistant with access to a document knowledge base.\n\n# Instructions\n- Always search for relevant documents before answering\n- Provide citations for your sources\n- Be objective and accurate in your responses\n- If multiple documents contain relevant information, synthesize them\n- Acknowledge when information is not available in the knowledge base",
      cache_control: { type: "ephemeral" }
    },
    {
      type: "text",
      text: "# Knowledge Base Context\n\nHere are the relevant documents for this conversation:\n\n## Document 1: Solar System Overview\nThe solar system consists of the Sun and all objects that orbit it...\n\n## Document 2: Planetary Characteristics\nEach planet has unique features. Mercury is the smallest planet...\n\n## Document 3: Mars Exploration\nMars has been a target of exploration for decades...\n\n[Additional documents...]",
      cache_control: { type: "ephemeral" }
    }
  ],
  messages: [
    {
      role: "user",
      content: "Can you search for information about Mars rovers?"
    },
    {
      role: "assistant",
      content: [
        {
          type: "tool_use",
          id: "tool_1",
          name: "search_documents",
          input: { query: "Mars rovers" }
        }
      ]
    },
    {
      role: "user",
      content: [
        {
          type: "tool_result",
          tool_use_id: "tool_1",
          content:
            "Found 3 relevant documents: Document 3 (Mars Exploration), Document 7 (Rover Technology), Document 9 (Mission History)"
        }
      ]
    },
    {
      role: "assistant",
      content: [
        {
          type: "text",
          text: "I found 3 relevant documents about Mars rovers. Let me get more details from the Mars Exploration document."
        }
      ]
    },
    {
      role: "user",
      content: [
        {
          type: "text",
          text: "Yes, please tell me about the Perseverance rover specifically.",
          cache_control: { type: "ephemeral" }
        }
      ]
    }
  ]
});
console.log(response);
```

```csharp C# hidelines={1..11,-2..}
using System;
using System.Collections.Generic;
using System.Text.Json;
using System.Threading.Tasks;
using Anthropic;
using Anthropic.Models.Messages;

public class Program
{
    public static async Task Main(string[] args)
    {
        AnthropicClient client = new()
        {
            ApiKey = Environment.GetEnvironmentVariable("ANTHROPIC_API_KEY")
        };

        var parameters = new MessageCreateParams
        {
            Model = Model.ClaudeOpus4_6,
            MaxTokens = 1024,
            Tools =
            [
                new ToolUnion(new Tool()
                {
                    Name = "search_documents",
                    Description = "Search through the knowledge base",
                    InputSchema = new InputSchema()
                    {
                        Properties = new Dictionary<string, JsonElement>
                        {
                            ["query"] = JsonSerializer.SerializeToElement(new { type = "string", description = "Search query" }),
                        },
                        Required = ["query"],
                    },
                }),
                new ToolUnion(new Tool()
                {
                    Name = "get_document",
                    Description = "Retrieve a specific document by ID",
                    InputSchema = new InputSchema()
                    {
                        Properties = new Dictionary<string, JsonElement>
                        {
                            ["doc_id"] = JsonSerializer.SerializeToElement(new { type = "string", description = "Document ID" }),
                        },
                        Required = ["doc_id"],
                    },
                    CacheControl = new CacheControlEphemeral(),
                }),
            ],
            System = new MessageCreateParamsSystem(new List<TextBlockParam>
            {
                new TextBlockParam()
                {
                    Text = "You are a helpful research assistant with access to a document knowledge base.\n\n# Instructions\n- Always search for relevant documents before answering\n- Provide citations for your sources\n- Be objective and accurate in your responses\n- If multiple documents contain relevant information, synthesize them\n- Acknowledge when information is not available in the knowledge base",
                    CacheControl = new CacheControlEphemeral(),
                },
                new TextBlockParam()
                {
                    Text = "# Knowledge Base Context\n\nHere are the relevant documents for this conversation:\n\n## Document 1: Solar System Overview\nThe solar system consists of the Sun and all objects that orbit it...\n\n## Document 2: Planetary Characteristics\nEach planet has unique features. Mercury is the smallest planet...\n\n## Document 3: Mars Exploration\nMars has been a target of exploration for decades...\n\n[Additional documents...]",
                    CacheControl = new CacheControlEphemeral(),
                },
            }),
            Messages =
            [
                new() { Role = Role.User, Content = "Can you search for information about Mars rovers?" },
                new()
                {
                    Role = Role.Assistant,
                    Content = new MessageParamContent(new List<ContentBlockParam>
                    {
                        new ContentBlockParam(new ToolUseBlockParam()
                        {
                            ID = "tool_1",
                            Name = "search_documents",
                            Input = new Dictionary<string, JsonElement>
                            {
                                ["query"] = JsonSerializer.SerializeToElement("Mars rovers"),
                            },
                        }),
                    }),
                },
                new()
                {
                    Role = Role.User,
                    Content = new MessageParamContent(new List<ContentBlockParam>
                    {
                        new ContentBlockParam(new ToolResultBlockParam()
                        {
                            ToolUseID = "tool_1",
                            Content = "Found 3 relevant documents: Document 3 (Mars Exploration), Document 7 (Rover Technology), Document 9 (Mission History)",
                        }),
                    }),
                },
                new()
                {
                    Role = Role.Assistant,
                    Content = "I found 3 relevant documents about Mars rovers. Let me get more details from the Mars Exploration document.",
                },
                new()
                {
                    Role = Role.User,
                    Content = new MessageParamContent(new List<ContentBlockParam>
                    {
                        new ContentBlockParam(new TextBlockParam()
                        {
                            Text = "Yes, please tell me about the Perseverance rover specifically.",
                            CacheControl = new CacheControlEphemeral(),
                        }),
                    }),
                },
            ]
        };

        var message = await client.Messages.Create(parameters);
        Console.WriteLine(message);
    }
}
```

```go Go hidelines={1..11,-1}
package main

import (
	"context"
	"fmt"
	"log"

	"github.com/anthropics/anthropic-sdk-go"
)

func main() {
	client := anthropic.NewClient()

	response, err := client.Messages.New(context.TODO(), anthropic.MessageNewParams{
		Model:     anthropic.ModelClaudeOpus4_6,
		MaxTokens: 1024,
		Tools: []anthropic.ToolUnionParam{
			{OfTool: &anthropic.ToolParam{
				Name:        "search_documents",
				Description: anthropic.String("Search through the knowledge base"),
				InputSchema: anthropic.ToolInputSchemaParam{
					Properties: map[string]any{
						"query": map[string]any{
							"type":        "string",
							"description": "Search query",
						},
					},
					Required: []string{"query"},
				},
			}},
			{OfTool: &anthropic.ToolParam{
				Name:        "get_document",
				Description: anthropic.String("Retrieve a specific document by ID"),
				InputSchema: anthropic.ToolInputSchemaParam{
					Properties: map[string]any{
						"doc_id": map[string]any{
							"type":        "string",
							"description": "Document ID",
						},
					},
					Required: []string{"doc_id"},
				},
				CacheControl: anthropic.NewCacheControlEphemeralParam(),
			}},
		},
		System: []anthropic.TextBlockParam{
			{
				Text:         "You are a helpful research assistant with access to a document knowledge base.\n\n# Instructions\n- Always search for relevant documents before answering\n- Provide citations for your sources\n- Be objective and accurate in your responses\n- If multiple documents contain relevant information, synthesize them\n- Acknowledge when information is not available in the knowledge base",
				CacheControl: anthropic.NewCacheControlEphemeralParam(),
			},
			{
				Text:         "# Knowledge Base Context\n\nHere are the relevant documents for this conversation:\n\n## Document 1: Solar System Overview\nThe solar system consists of the Sun and all objects that orbit it...\n\n## Document 2: Planetary Characteristics\nEach planet has unique features. Mercury is the smallest planet...\n\n## Document 3: Mars Exploration\nMars has been a target of exploration for decades...\n\n[Additional documents...]",
				CacheControl: anthropic.NewCacheControlEphemeralParam(),
			},
		},
		Messages: []anthropic.MessageParam{
			anthropic.NewUserMessage(anthropic.NewTextBlock("Can you search for information about Mars rovers?")),
			anthropic.NewAssistantMessage(anthropic.NewToolUseBlock(
				"tool_1",
				map[string]any{"query": "Mars rovers"},
				"search_documents",
			)),
			anthropic.NewUserMessage(anthropic.NewToolResultBlock(
				"tool_1",
				"Found 3 relevant documents: Document 3 (Mars Exploration), Document 7 (Rover Technology), Document 9 (Mission History)",
				false,
			)),
			anthropic.NewAssistantMessage(anthropic.NewTextBlock("I found 3 relevant documents about Mars rovers. Let me get more details from the Mars Exploration document.")),
			{
				Role: anthropic.MessageParamRoleUser,
				Content: []anthropic.ContentBlockParamUnion{
					{OfText: &anthropic.TextBlockParam{
						Text:         "Yes, please tell me about the Perseverance rover specifically.",
						CacheControl: anthropic.NewCacheControlEphemeralParam(),
					}},
				},
			},
		},
	})
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(response)
}
```

```java Java hidelines={1..3,5..19,-2..}
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.Tool.InputSchema;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import java.util.List;
import java.util.Map;

public class MultipleCacheBreakpointsExample {

  public static void main(String[] args) {
    AnthropicClient client = AnthropicOkHttpClient.fromEnv();

    // Search tool schema
    InputSchema searchSchema = InputSchema.builder()
      .properties(
        JsonValue.from(
          Map.of("query", Map.of("type", "string", "description", "Search query"))
        )
      )
      .putAdditionalProperty("required", JsonValue.from(List.of("query")))
      .build();

    // Get document tool schema
    InputSchema getDocSchema = InputSchema.builder()
      .properties(
        JsonValue.from(
          Map.of("doc_id", Map.of("type", "string", "description", "Document ID"))
        )
      )
      .putAdditionalProperty("required", JsonValue.from(List.of("doc_id")))
      .build();

    MessageCreateParams params = MessageCreateParams.builder()
      .model(Model.CLAUDE_OPUS_4_6)
      .maxTokens(1024)
      // Tools with cache control on the last one
      .addTool(
        Tool.builder()
          .name("search_documents")
          .description("Search through the knowledge base")
          .inputSchema(searchSchema)
          .build()
      )
      .addTool(
        Tool.builder()
          .name("get_document")
          .description("Retrieve a specific document by ID")
          .inputSchema(getDocSchema)
          .cacheControl(CacheControlEphemeral.builder().build())
          .build()
      )
      // System prompts with cache control on instructions and context separately
      .systemOfTextBlockParams(
        List.of(
          TextBlockParam.builder()
            .text(
              "You are a helpful research assistant with access to a document knowledge base.\n\n# Instructions\n- Always search for relevant documents before answering\n- Provide citations for your sources\n- Be objective and accurate in your responses\n- If multiple documents contain relevant information, synthesize them\n- Acknowledge when information is not available in the knowledge base"
            )
            .cacheControl(CacheControlEphemeral.builder().build())
            .build(),
          TextBlockParam.builder()
            .text(
              "# Knowledge Base Context\n\nHere are the relevant documents for this conversation:\n\n## Document 1: Solar System Overview\nThe solar system consists of the Sun and all objects that orbit it...\n\n## Document 2: Planetary Characteristics\nEach planet has unique features. Mercury is the smallest planet...\n\n## Document 3: Mars Exploration\nMars has been a target of exploration for decades...\n\n[Additional documents...]"
            )
            .cacheControl(CacheControlEphemeral.builder().build())
            .build()
        )
      )
      // Conversation history
      .addUserMessage("Can you search for information about Mars rovers?")
      .addAssistantMessageOfBlockParams(
        List.of(
          ContentBlockParam.ofToolUse(
            ToolUseBlockParam.builder()
              .id("tool_1")
              .name("search_documents")
              .input(JsonValue.from(Map.of("query", "Mars rovers")))
              .build()
          )
        )
      )
      .addUserMessageOfBlockParams(
        List.of(
          ContentBlockParam.ofToolResult(
            ToolResultBlockParam.builder()
              .toolUseId("tool_1")
              .content(
                "Found 3 relevant documents: Document 3 (Mars Exploration), Document 7 (Rover Technology), Document 9 (Mission History)"
              )
              .build()
          )
        )
      )
      .addAssistantMessageOfBlockParams(
        List.of(
          ContentBlockParam.ofText(
            TextBlockParam.builder()
              .text(
                "I found 3 relevant documents about Mars rovers. Let me get more details from the Mars Exploration document."
              )
              .build()
          )
        )
      )
      .addUserMessageOfBlockParams(
        List.of(
          ContentBlockParam.ofText(
            TextBlockParam.builder()
              .text("Yes, please tell me about the Perseverance rover specifically.")
              .cacheControl(CacheControlEphemeral.builder().build())
              .build()
          )
        )
      )
      .build();

    Message message = client.messages().create(params);
    System.out.println(message);
  }
}
```

```php PHP hidelines={1..4}
<?php

use Anthropic\Client;

$client = new Client(apiKey: getenv("ANTHROPIC_API_KEY"));

$message = $client->messages->create(
    maxTokens: 1024,
    messages: [
        [
            'role' => 'user',
            'content' => 'Can you search for information about Mars rovers?'
        ],
        [
            'role' => 'assistant',
            'content' => [
                [
                    'type' => 'tool_use',
                    'id' => 'tool_1',
                    'name' => 'search_documents',
                    'input' => ['query' => 'Mars rovers']
                ]
            ]
        ],
        [
            'role' => 'user',
            'content' => [
                [
                    'type' => 'tool_result',
                    'tool_use_id' => 'tool_1',
                    'content' => 'Found 3 relevant documents: Document 3 (Mars Exploration), Document 7 (Rover Technology), Document 9 (Mission History)'
                ]
            ]
        ],
        [
            'role' => 'assistant',
            'content' => [
                [
                    'type' => 'text',
                    'text' => 'I found 3 relevant documents about Mars rovers. Let me get more details from the Mars Exploration document.'
                ]
            ]
        ],
        [
            'role' => 'user',
            'content' => [
                [
                    'type' => 'text',
                    'text' => 'Yes, please tell me about the Perseverance rover specifically.',
                    'cache_control' => ['type' => 'ephemeral']
                ]
            ]
        ]
    ],
    model: 'claude-opus-4-6',
    system: [
        [
            'type' => 'text',
            'text' => "You are a helpful research assistant with access to a document knowledge base.\n\n# Instructions\n- Always search for relevant documents before answering\n- Provide citations for your sources\n- Be objective and accurate in your responses\n- If multiple documents contain relevant information, synthesize them\n- Acknowledge when information is not available in the knowledge base",
            'cache_control' => ['type' => 'ephemeral']
        ],
        [
            'type' => 'text',
            'text' => "# Knowledge Base Context\n\nHere are the relevant documents for this conversation:\n\n## Document 1: Solar System Overview\nThe solar system consists of the Sun and all objects that orbit it...\n\n## Document 2: Planetary Characteristics\nEach planet has unique features. Mercury is the smallest planet...\n\n## Document 3: Mars Exploration\nMars has been a target of exploration for decades...\n\n[Additional documents...]",
            'cache_control' => ['type' => 'ephemeral']
        ]
    ],
    tools: [
        [
            'name' => 'search_documents',
            'description' => 'Search through the knowledge base',
            'input_schema' => [
                'type' => 'object',
                'properties' => [
                    'query' => [
                        'type' => 'string',
                        'description' => 'Search query'
                    ]
                ],
                'required' => ['query']
            ]
        ],
        [
            'name' => 'get_document',
            'description' => 'Retrieve a specific document by ID',
            'input_schema' => [
                'type' => 'object',
                'properties' => [
                    'doc_id' => [
                        'type' => 'string',
                        'description' => 'Document ID'
                    ]
                ],
                'required' => ['doc_id']
            ],
            'cache_control' => ['type' => 'ephemeral']
        ]
    ],
);

echo $message;
```

```ruby Ruby nocheck hidelines={1..2}
require "anthropic"

client = Anthropic::Client.new

message = client.messages.create(
  model: "claude-opus-4-6",
  max_tokens: 1024,
  tools: [
    {
      name: "search_documents",
      description: "Search through the knowledge base",
      input_schema: {
        type: "object",
        properties: {
          query: {
            type: "string",
            description: "Search query"
          }
        },
        required: ["query"]
      }
    },
    {
      name: "get_document",
      description: "Retrieve a specific document by ID",
      input_schema: {
        type: "object",
        properties: {
          doc_id: {
            type: "string",
            description: "Document ID"
          }
        },
        required: ["doc_id"]
      },
      cache_control: { type: "ephemeral" }
    }
  ],
  system: [
    {
      type: "text",
      text: "You are a helpful research assistant with access to a document knowledge base.\n\n# Instructions\n- Always search for relevant documents before answering\n- Provide citations for your sources\n- Be objective and accurate in your responses\n- If multiple documents contain relevant information, synthesize them\n- Acknowledge when information is not available in the knowledge base",
      cache_control: { type: "ephemeral" }
    },
    {
      type: "text",
      text: "# Knowledge Base Context\n\nHere are the relevant documents for this conversation:\n\n## Document 1: Solar System Overview\nThe solar system consists of the Sun and all objects that orbit it...\n\n## Document 2: Planetary Characteristics\nEach planet has unique features. Mercury is the smallest planet...\n\n## Document 3: Mars Exploration\nMars has been a target of exploration for decades...\n\n[Additional documents...]",
      cache_control: { type: "ephemeral" }
    }
  ],
  messages: [
    {
      role: "user",
      content: "Can you search for information about Mars rovers?"
    },
    {
      role: "assistant",
      content: [
        {
          type: "tool_use",
          id: "tool_1",
          name: "search_documents",
          input: { query: "Mars rovers" }
        }
      ]
    },
    {
      role: "user",
      content: [
        {
          type: "tool_result",
          tool_use_id: "tool_1",
          content: "Found 3 relevant documents: Document 3 (Mars Exploration), Document 7 (Rover Technology), Document 9 (Mission History)"
        }
      ]
    },
    {
      role: "assistant",
      content: [
        {
          type: "text",
          text: "I found 3 relevant documents about Mars rovers. Let me get more details from the Mars Exploration document."
        }
      ]
    },
    {
      role: "user",
      content: [
        {
          type: "text",
          text: "Yes, please tell me about the Perseverance rover specifically.",
          cache_control: { type: "ephemeral" }
        }
      ]
    }
  ]
)
puts message
```
</CodeGroup>

这个综合示例演示了如何使用所有4个可用的缓存断点来优化提示词的不同部分：

1. **工具缓存**（缓存断点1）：最后一个工具定义上的`cache_control`参数缓存所有工具定义。

2. **可重用指令缓存**（缓存断点2）：系统提示中的静态指令被单独缓存。这些指令在请求之间很少改变。

3. **RAG上下文缓存**（缓存断点3）：知识库文档被独立缓存，允许您更新RAG文档而不会使工具或指令缓存失效。

4. **对话历史缓存**（缓存断点4）：助手的响应被标记为`cache_control`以启用对话的增量缓存。

这种方法提供了最大的灵活性：
- 如果您仅更新最后的用户消息，所有四个缓存段都会被重用
- 如果您更新RAG文档但保持相同的工具和指令，前两个缓存段会被重用
- 如果您更改对话但保持相同的工具、指令和文档，前三个段会被重用
- 每个缓存断点可以根据应用程序中的更改内容独立失效

对于第一个请求：
- `input_tokens`：最后一条用户消息中的令牌数
- `cache_creation_input_tokens`：所有缓存段中的令牌数（工具+指令+RAG文档+对话历史）
- `cache_read_input_tokens`：0（无缓存命中）

对于仅有新用户消息的后续请求：
- `input_tokens`：仅新用户消息中的令牌数
- `cache_creation_input_tokens`：添加到对话历史的任何新令牌
- `cache_read_input_tokens`：所有先前缓存的令牌（工具+指令+RAG文档+先前对话）

这种模式对以下情况特别强大：
- 具有大型文档上下文的RAG应用程序
- 使用多个工具的代理系统
- 需要维护上下文的长期运行对话
- 需要独立优化提示词不同部分的应用程序

</section>

## 数据保留

提示词缓存（自动和显式）符合 ZDR 资格。Anthropic 不存储您的提示词或 Claude 响应的原始文本。

KV（键值）缓存表示和缓存内容的密码学哈希值仅保存在内存中，不会存储在静止状态。缓存条目的最小生命周期为 5 分钟（标准）或 60 分钟（扩展），之后会被迅速（但不是立即）删除。缓存条目在组织之间是隔离的。

有关所有功能的 ZDR 资格，请参阅 [API 和数据保留](/docs/zh-CN/build-with-claude/api-and-data-retention)。

---
## 常见问题

  <section title="我需要多个缓存断点还是在末尾放一个就足够了？">

    **在大多数情况下，在静态内容末尾放置单个缓存断点就足够了。** 缓存写入仅在您标记的块处发生。将其放在跨请求保持相同的最后一个块上，每个后续请求都会读取该相同的条目。如果后面的块因请求而异（时间戳、传入消息），请在它之前的最后一个稳定块上保持断点。

    您只需要多个断点，如果：
    - 不断增长的对话将您的断点推向最后一次缓存写入之后 20 个或更多块，将先前的条目置于回溯窗口之外
    - 您想独立缓存以不同频率更新的部分
    - 您需要对缓存内容进行显式控制以优化成本

    示例：如果您有系统指令（很少更改）和 RAG 上下文（每天更改），您可能会使用两个断点来分别缓存它们。
  
</section>

  <section title="缓存断点会增加额外成本吗？">

    不会，缓存断点本身是免费的。您只需支付：
    - 将内容写入缓存（比基础输入令牌多 25%，用于 5 分钟 TTL）
    - 从缓存读取（基础输入令牌价格的 10%）
    - 未缓存内容的常规输入令牌

    断点的数量不会影响定价 - 只有缓存和读取的内容量才重要。
  
</section>

  <section title="我如何从使用字段计算总输入令牌？">

    使用响应包括三个单独的输入令牌字段，它们共同代表您的总输入：

    ```text
    total_input_tokens = cache_read_input_tokens + cache_creation_input_tokens + input_tokens
    ```

    - `cache_read_input_tokens`：从缓存检索的令牌（缓存断点之前被缓存的所有内容）
    - `cache_creation_input_tokens`：被写入缓存的新令牌（在缓存断点处）
    - `input_tokens`：最后一个缓存断点之后未被缓存的令牌

    **重要：** `input_tokens` 不代表所有输入令牌 - 仅代表最后一个缓存断点之后的部分。如果您有缓存内容，`input_tokens` 通常会远小于您的总输入。

    **示例：** 使用缓存的 200k 令牌文档和 50 令牌用户问题：
    - `cache_read_input_tokens`：200,000
    - `cache_creation_input_tokens`：0
    - `input_tokens`：50
    - **总计**：200,050 令牌

    这种分解对于理解您的成本和速率限制使用都至关重要。有关更多详细信息，请参阅 [跟踪缓存性能](#tracking-cache-performance)。
  
</section>

  <section title="缓存生命周期是多少？">

    缓存的默认最小生命周期 (TTL) 是 5 分钟。每次使用缓存内容时，此生命周期都会刷新。

    如果您发现 5 分钟太短，Anthropic 还提供 [1 小时缓存 TTL](#1-hour-cache-duration)。
  
</section>

  <section title="我可以使用多少个缓存断点？">

    您可以在提示词中定义最多 4 个缓存断点（使用 `cache_control` 参数）。
  
</section>

  <section title="提示词缓存是否适用于所有模型？">

    提示词缓存在所有 [活跃 Claude 模型](/docs/zh-CN/about-claude/models/overview) 上受支持。
  
</section>

  <section title="提示词缓存如何与扩展思考配合使用？">

    缓存的系统提示词和工具在思考参数更改时将被重用。但是，思考更改（启用/禁用或预算更改）将使之前缓存的带有消息内容的提示词前缀失效。

    有关缓存失效的更多详细信息，请参阅 [什么使缓存失效](#what-invalidates-the-cache)。

    有关扩展思考的更多信息，包括其与工具使用和提示词缓存的交互，请参阅 [扩展思考文档](/docs/zh-CN/build-with-claude/extended-thinking#extended-thinking-and-prompt-caching)。
  
</section>

  <section title="我如何启用提示词缓存？">

    最简单的方法是在请求体的顶级添加 `"cache_control": {"type": "ephemeral"}`（[自动缓存](#automatic-caching)）。或者，在单个内容块上包含至少一个 `cache_control` 断点（[显式缓存断点](#explicit-cache-breakpoints)）。
  
</section>

  <section title="我可以将提示词缓存与其他 API 功能一起使用吗？">

    是的，提示词缓存可以与其他 API 功能（如工具使用和视觉功能）一起使用。但是，更改提示词中是否有图像或修改工具使用设置将破坏缓存。

    有关缓存失效的更多详细信息，请参阅 [什么使缓存失效](#what-invalidates-the-cache)。
  
</section>

  <section title="提示词缓存如何影响定价？">

    提示词缓存引入了新的定价结构，其中缓存写入成本比基础输入令牌多 25%，而缓存命中仅成本基础输入令牌价格的 10%。
  
</section>

  <section title="我可以手动清除缓存吗？">

    目前，没有办法手动清除缓存。缓存前缀在最少 5 分钟不活动后自动过期。
  
</section>

  <section title="我如何跟踪缓存策略的有效性？">

    您可以使用 API 响应中的 `cache_creation_input_tokens` 和 `cache_read_input_tokens` 字段监控缓存性能。
  
</section>

  <section title="什么会破坏缓存？">

    有关缓存失效的更多详细信息，请参阅 [什么使缓存失效](#what-invalidates-the-cache)，包括需要创建新缓存条目的更改列表。
  
</section>

  <section title="提示词缓存如何处理隐私和数据分离？">

提示词缓存设计有强大的隐私和数据分离措施：

1. 缓存键使用缓存控制点之前的提示词的密码学哈希生成。这意味着只有具有相同提示词的请求才能访问特定缓存。

2. 缓存是特定于组织的。同一组织内的用户如果使用相同的提示词可以访问相同的缓存，但缓存不会在不同组织之间共享，即使提示词相同。

3. 缓存机制设计用于维护每个唯一对话或上下文的完整性和隐私。

4. 在提示词中的任何地方使用 `cache_control` 是安全的。为了使缓存产生读取，将断点放在稳定前缀的末尾：将其放在每个请求都会更改的块上（例如时间戳或用户的任意输入）会每次写入新条目，永远不会命中。

这些措施确保提示词缓存在提供性能优势的同时维护数据隐私和安全。

注意：从 2026 年 2 月 5 日开始，缓存将按工作区而不是按组织隔离。此更改适用于 Claude API 和 Azure AI Foundry（预览版）。有关详细信息，请参阅 [缓存存储和共享](#cache-storage-and-sharing)。

  
</section>
  <section title="我可以将提示词缓存与批处理 API 一起使用吗？">

    是的，可以将提示词缓存与您的 [批处理 API](/docs/zh-CN/build-with-claude/batch-processing) 请求一起使用。但是，由于异步批处理请求可以并发处理且顺序任意，缓存命中是尽力而为的基础。

    [1 小时缓存](#1-hour-cache-duration) 可以帮助改进您的缓存命中。使用它最具成本效益的方式如下：
    - 收集一组具有共享前缀的消息请求。
    - 发送仅包含一个具有此共享前缀和 1 小时缓存块的请求的批处理请求。这将被写入 1 小时缓存。
    - 完成后立即提交其余请求。您必须监控作业以了解何时完成。

    这通常比使用 5 分钟缓存更好，因为批处理请求通常需要 5 分钟到 1 小时才能完成。Anthropic 正在考虑改进这些缓存命中率的方法，并使此过程更加直接。
  
</section>
  <section title="为什么我在 Python 中看到错误 `AttributeError: 'Beta' object has no attribute 'prompt_caching'`？">

  当您升级了 SDK 或使用过时的代码示例时，通常会出现此错误。提示词缓存现在已普遍可用，因此您不再需要 beta 前缀。而不是：
    <CodeGroup>
      
      ```python Python nocheck
      client.beta.prompt_caching.messages.create(**params)
      ```

      
      ```typescript TypeScript nocheck hidelines={1..2}
      import Anthropic from "@anthropic-ai/sdk";

      const client = new Anthropic();

      const response = await client.beta.promptCaching.messages.create({
        model: "claude-opus-4-6",
        max_tokens: 1024,
        system: [
          {
            type: "text",
            text: "You are an expert on this large document...",
            cache_control: { type: "ephemeral" }
          }
        ],
        messages: [{ role: "user", content: "Summarize the key points" }]
      });

      console.log(response);
      ```

      
      ```php PHP hidelines={1..4} nocheck
      <?php

      use Anthropic\Client;

      $client = new Client(apiKey: getenv("ANTHROPIC_API_KEY"));

      $message = $client->beta->promptCaching->messages->create(
          maxTokens: 1024,
          messages: [
              ['role' => 'user', 'content' => 'Summarize the key points']
          ],
          model: 'claude-opus-4-6',
          system: [
              [
                  'type' => 'text',
                  'text' => 'You are an expert on this large document...',
                  'cache_control' => ['type' => 'ephemeral']
              ]
          ],
      );

      echo $message->content[0]->text;
      ```
    </CodeGroup>
    只需使用：
    <CodeGroup>
      
      ```python Python nocheck
      client.messages.create(**params)
      ```

      ```typescript TypeScript hidelines={1..2}
      import Anthropic from "@anthropic-ai/sdk";

      const client = new Anthropic();

      const response = await client.messages.create({
        model: "claude-opus-4-6",
        max_tokens: 1024,
        system: [
          {
            type: "text",
            text: "You are an expert on this large document...",
            cache_control: { type: "ephemeral" }
          }
        ],
        messages: [{ role: "user", content: "Summarize the key points" }]
      });

      console.log(response);
      ```

      ```php PHP hidelines={1..4}
      <?php

      use Anthropic\Client;

      $client = new Client(apiKey: getenv("ANTHROPIC_API_KEY"));

      $message = $client->messages->create(
          maxTokens: 1024,
          messages: [
              ['role' => 'user', 'content' => 'Summarize the key points']
          ],
          model: 'claude-opus-4-6',
          system: [
              [
                  'type' => 'text',
                  'text' => 'You are an expert on this large document...',
                  'cache_control' => ['type' => 'ephemeral']
              ]
          ],
      );

      echo $message->content[0]->text;
      ```

      ```ruby Ruby hidelines={1..2}
      require "anthropic"

      client = Anthropic::Client.new

      message = client.messages.create(
        model: "claude-opus-4-6",
        max_tokens: 1024,
        system: [
          {
            type: "text",
            text: "You are an expert on this large document...",
            cache_control: { type: "ephemeral" }
          }
        ],
        messages: [
          { role: "user", content: "Summarize the key points" }
        ]
      )
      puts message.content.first.text
      ```
    </CodeGroup>
  
</section>
  <section title="为什么我看到 'TypeError: Cannot read properties of undefined (reading 'messages')'？">

  当您升级了 SDK 或使用过时的代码示例时，通常会出现此错误。提示词缓存现在已普遍可用，因此您不再需要 beta 前缀。而不是：

      ```typescript TypeScript
      client.beta.promptCaching.messages.create(/* ... */);
      ```

      只需使用：

      ```typescript
      client.messages.create(/* ... */);
      ```
  
</section>