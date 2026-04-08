import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { listGolems } from '../../lib/api/golemsApi';
import {
  bindGolemPolicyGroup,
  createPolicyGroup,
  getPolicyGroup,
  listPolicyGroupVersions,
  listPolicyGroups,
  publishPolicyGroup,
  rollbackPolicyGroup,
  type PolicyDraftSpec,
  unbindGolemPolicyGroup,
  updatePolicyGroupDraft,
} from '../../lib/api/policiesApi';
import {
  PolicyDraftSection,
  PolicyGroupHeaderSection,
  PolicyGroupsSidebar,
} from './PolicyGroupsPageSections';
import { PolicyBindingsSection, PolicyGroupEmptyState, PolicyReleaseRail } from './PolicyGroupsPageDetailSections';
import { EMPTY_DRAFT_SPEC, summarizeRollout, toEditableDraft } from './PolicyGroupsPageSupport';

interface CreatePolicyFormState {
  slug: string;
  name: string;
  description: string;
}

export function PolicyGroupsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { groupId } = useParams<{ groupId: string }>();
  const [createForm, setCreateForm] = useState<CreatePolicyFormState>({ slug: '', name: '', description: '' });
  const [draftEditorValue, setDraftEditorValue] = useState(JSON.stringify(EMPTY_DRAFT_SPEC, null, 2));
  const [draftEditorError, setDraftEditorError] = useState<string | null>(null);
  const [publishSummary, setPublishSummary] = useState('');
  const [selectedAttachGolemId, setSelectedAttachGolemId] = useState('');

  const policiesQuery = useQuery({ queryKey: ['policy-groups'], queryFn: listPolicyGroups });
  const golemsQuery = useQuery({ queryKey: ['golems'], queryFn: () => listGolems() });
  const selectedGroupId = groupId ?? policiesQuery.data?.[0]?.id ?? null;

  const policyQuery = useQuery({
    queryKey: ['policy-group', selectedGroupId],
    queryFn: () => getPolicyGroup(selectedGroupId ?? ''),
    enabled: Boolean(selectedGroupId),
  });
  const versionsQuery = useQuery({
    queryKey: ['policy-group-versions', selectedGroupId],
    queryFn: () => listPolicyGroupVersions(selectedGroupId ?? ''),
    enabled: Boolean(selectedGroupId),
  });

  useEffect(() => {
    if (!groupId && policiesQuery.data?.length) {
      navigate(`/policies/${policiesQuery.data[0].id}`, { replace: true });
    }
  }, [groupId, navigate, policiesQuery.data]);

  useEffect(() => {
    setDraftEditorValue(JSON.stringify(toEditableDraft(policyQuery.data?.draftSpec), null, 2));
    setDraftEditorError(null);
  }, [policyQuery.data?.id, policyQuery.data?.draftSpec]);

  const invalidateSelectedPolicyViews = async () => {
    const tasks = [queryClient.invalidateQueries({ queryKey: ['policy-groups'] })];
    if (selectedGroupId) {
      tasks.push(queryClient.invalidateQueries({ queryKey: ['policy-group', selectedGroupId] }));
      tasks.push(queryClient.invalidateQueries({ queryKey: ['policy-group-versions', selectedGroupId] }));
    }
    await Promise.all(tasks);
  };

  const invalidateRolloutViews = async () => {
    await Promise.all([invalidateSelectedPolicyViews(), queryClient.invalidateQueries({ queryKey: ['golems'] })]);
  };

  const createPolicyMutation = useMutation({
    mutationFn: createPolicyGroup,
    onSuccess: async (createdPolicy) => {
      await queryClient.invalidateQueries({ queryKey: ['policy-groups'] });
      setCreateForm({ slug: '', name: '', description: '' });
      navigate(`/policies/${createdPolicy.id}`);
    },
  });
  const updateDraftMutation = useMutation({
    mutationFn: async (draftSpec: PolicyDraftSpec) => {
      if (!selectedGroupId) {
        return null;
      }
      return updatePolicyGroupDraft(selectedGroupId, draftSpec);
    },
    onSuccess: invalidateSelectedPolicyViews,
  });
  const publishMutation = useMutation({
    mutationFn: async (changeSummary: string) => {
      if (!selectedGroupId) {
        return null;
      }
      return publishPolicyGroup(selectedGroupId, changeSummary);
    },
    onSuccess: async () => {
      await invalidateRolloutViews();
      setPublishSummary('');
    },
  });
  const rollbackMutation = useMutation({
    mutationFn: async (version: number) => {
      if (!selectedGroupId) {
        return null;
      }
      return rollbackPolicyGroup(selectedGroupId, version, `Rollback to v${version} from Hive UI`);
    },
    onSuccess: invalidateRolloutViews,
  });
  const bindMutation = useMutation({
    mutationFn: async (golemId: string) => {
      if (!selectedGroupId) {
        return null;
      }
      return bindGolemPolicyGroup(golemId, selectedGroupId);
    },
    onSuccess: async () => {
      await invalidateRolloutViews();
      setSelectedAttachGolemId('');
    },
  });
  const unbindMutation = useMutation({
    mutationFn: unbindGolemPolicyGroup,
    onSuccess: invalidateRolloutViews,
  });

  const selectedPolicy = policyQuery.data ?? null;
  const allGolems = golemsQuery.data ?? [];
  const boundGolems = selectedGroupId
    ? allGolems.filter((golem) => golem.policyBinding?.policyGroupId === selectedGroupId)
    : [];
  const selectedRollout = selectedPolicy ? summarizeRollout(selectedPolicy.id, allGolems) : null;

  const handleCreateFormChange = (field: keyof CreatePolicyFormState, value: string) => {
    setCreateForm((current) => ({ ...current, [field]: value }));
  };
  const handleCreateSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    createPolicyMutation.mutate({
      slug: createForm.slug.trim(),
      name: createForm.name.trim(),
      description: createForm.description.trim() || undefined,
    });
  };
  const handleDraftEditorChange = (value: string) => {
    setDraftEditorValue(value);
    if (draftEditorError) {
      setDraftEditorError(null);
    }
  };
  const handleSaveDraft = async () => {
    try {
      const parsed = JSON.parse(draftEditorValue) as PolicyDraftSpec;
      setDraftEditorError(null);
      await updateDraftMutation.mutateAsync(parsed);
    } catch (error) {
      setDraftEditorError(error instanceof Error ? error.message : 'Invalid JSON payload');
    }
  };
  const handleBindSelected = async () => {
    if (!selectedAttachGolemId) {
      return;
    }
    await bindMutation.mutateAsync(selectedAttachGolemId);
  };
  const handlePublish = async () => {
    await publishMutation.mutateAsync(publishSummary.trim() || 'Published from Hive UI');
  };
  const handleRollback = async (version: number) => {
    await rollbackMutation.mutateAsync(version);
  };
  const handleUnbind = async (golemId: string) => {
    await unbindMutation.mutateAsync(golemId);
  };

  return (
    <div className="grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)]">
      <PolicyGroupsSidebar
        policies={policiesQuery.data ?? []}
        allGolems={allGolems}
        selectedGroupId={selectedGroupId}
        createForm={createForm}
        isCreating={createPolicyMutation.isPending}
        onCreateFieldChange={handleCreateFormChange}
        onCreateSubmit={handleCreateSubmit}
      />

      <div className="grid gap-4">
        {selectedPolicy && selectedRollout ? (
          <>
            <PolicyGroupHeaderSection
              policy={selectedPolicy}
              boundGolems={boundGolems}
              rollout={selectedRollout}
            />

            <section className="grid gap-4 2xl:grid-cols-[minmax(0,1.1fr)_minmax(320px,0.9fr)]">
              <PolicyDraftSection
                draftSpec={selectedPolicy.draftSpec}
                draftEditorValue={draftEditorValue}
                draftEditorError={draftEditorError}
                isSaving={updateDraftMutation.isPending}
                onDraftEditorChange={handleDraftEditorChange}
                onSaveDraft={handleSaveDraft}
              />
              <PolicyReleaseRail
                versions={versionsQuery.data ?? []}
                currentVersion={selectedPolicy.currentVersion}
                publishSummary={publishSummary}
                isPublishing={publishMutation.isPending}
                isRollingBack={rollbackMutation.isPending}
                onPublishSummaryChange={setPublishSummary}
                onPublish={handlePublish}
                onRollback={handleRollback}
              />
            </section>

            <PolicyBindingsSection
              allGolems={allGolems}
              boundGolems={boundGolems}
              selectedAttachGolemId={selectedAttachGolemId}
              isBinding={bindMutation.isPending}
              isUnbinding={unbindMutation.isPending}
              onSelectedAttachGolemIdChange={setSelectedAttachGolemId}
              onBindSelected={handleBindSelected}
              onUnbind={handleUnbind}
            />
          </>
        ) : (
          <PolicyGroupEmptyState />
        )}
      </div>
    </div>
  );
}
