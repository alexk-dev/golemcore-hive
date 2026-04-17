import { describe, expect, it } from 'vitest';
import type { PolicyGroupSpecResponse } from '../../lib/api/policiesApi';
import { toEditableDraft } from './PolicyGroupsPageSupport';

describe('PolicyGroupsPageSupport', () => {
  it('shouldPreserveTierFallbackRoutingFieldsWhenConvertingDraftSpec()', () => {
    const spec = {
      schemaVersion: 1,
      llmProviders: {},
      modelRouter: {
        temperature: 0.7,
        routing: {
          model: 'openai/gpt-5.1',
          reasoning: 'low',
          temperature: 0.4,
          fallbackMode: 'weighted',
          fallbacks: [
            {
              model: 'openai/gpt-5.1-mini',
              reasoning: 'low',
              temperature: 0.3,
              weight: 0.75,
            },
          ],
        },
        tiers: {
          balanced: {
            model: 'openai/gpt-5.1',
            reasoning: 'low',
            temperature: 0.5,
            fallbackMode: 'sequential',
            fallbacks: [
              {
                model: 'openai/gpt-4.1-mini',
                reasoning: 'low',
                temperature: 0.2,
                weight: 1.0,
              },
            ],
          },
        },
        dynamicTierEnabled: true,
      },
      modelCatalog: {
        defaultModel: 'openai/gpt-5.1',
        models: {},
      },
      tools: null,
      memory: null,
      mcp: null,
      autonomy: null,
      sdlc: null,
      checksum: 'checksum-1',
    } as unknown as PolicyGroupSpecResponse;

    const draft = toEditableDraft(spec);

    expect(draft.modelRouter?.routing).toEqual({
      model: 'openai/gpt-5.1',
      reasoning: 'low',
      temperature: 0.4,
      fallbackMode: 'weighted',
      fallbacks: [
        {
          model: 'openai/gpt-5.1-mini',
          reasoning: 'low',
          temperature: 0.3,
          weight: 0.75,
        },
      ],
    });
    expect(draft.modelRouter?.tiers?.balanced).toEqual({
      model: 'openai/gpt-5.1',
      reasoning: 'low',
      temperature: 0.5,
      fallbackMode: 'sequential',
      fallbacks: [
        {
          model: 'openai/gpt-4.1-mini',
          reasoning: 'low',
          temperature: 0.2,
          weight: 1.0,
        },
      ],
    });
  });
});
