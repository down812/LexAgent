<template>
  <div class="page">
    <!-- 头部 -->
    <div class="header">
      <div class="header-main">
        <el-button link @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <div class="title-wrap" v-loading="loading">
          <template v-if="caseInfo">
            <span class="case-name">{{ caseInfo.caseName }}</span>
            <el-tag :type="CASE_STATUS_TAG[caseInfo.status] || 'info'" effect="plain" size="small">
              {{ CASE_STATUS_LABELS[caseInfo.status] || caseInfo.status }}
            </el-tag>
            <span class="case-no">{{ caseInfo.caseNo }}</span>
          </template>
        </div>
      </div>
    </div>

    <!-- Tab 区 -->
    <div class="card">
      <el-tabs v-model="activeTab">
        <!-- 1. 基本信息 -->
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions v-if="caseInfo" :column="2" border class="desc">
            <el-descriptions-item label="案件编号">{{ caseInfo.caseNo }}</el-descriptions-item>
            <el-descriptions-item label="案件类型">{{ caseInfo.caseType }}</el-descriptions-item>
            <el-descriptions-item label="发生时间" :span="2">
              {{ caseInfo.occurredAt || '未填写' }}
            </el-descriptions-item>
            <el-descriptions-item label="发生地点">{{ caseInfo.location || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="涉及主体">{{ caseInfo.partiesInvolved || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="案件描述" :span="2">
              <div class="pre-wrap">{{ caseInfo.description }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">
              <div class="pre-wrap">{{ caseInfo.remark || '无' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="证据数量">{{ caseInfo.evidenceCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="问诊次数">{{ caseInfo.conversationCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>
          <div class="tab-actions">
            <el-button type="primary" @click="openEdit">编辑案件信息</el-button>
            <el-button
              v-if="caseInfo && caseInfo.status !== CASE_STATUS.ARCHIVED"
              type="warning"
              plain
              @click="doArchive"
            >归档案件</el-button>
          </div>
        </el-tab-pane>

        <!-- 2. 案件事实 -->
        <el-tab-pane label="案件事实" name="facts">
          <div class="fact-add">
            <el-input
              v-model="newFact"
              type="textarea"
              :rows="2"
              maxlength="2000"
              show-word-limit
              placeholder="补充案件事实，例如：公司自2026年8月起拖欠三个月工资……"
            />
            <el-button type="primary" :loading="addingFact" :disabled="!newFact.trim()" @click="doAddFact">
              添加事实
            </el-button>
          </div>
          <div v-loading="factsLoading" class="fact-list">
            <div v-for="fact in facts" :key="fact.id" class="fact-item">
              <div class="fact-content">{{ fact.factContent }}</div>
              <div class="fact-meta">
                <el-tag size="small" effect="plain" type="info">{{ FACT_SOURCE_LABELS[fact.factSource] || fact.factSource }}</el-tag>
                <el-tag v-if="fact.confirmed" size="small" effect="plain" type="success">已确认</el-tag>
                <span class="fact-time">{{ formatDateTime(fact.createdAt) }}</span>
                <el-button link type="danger" size="small" @click="doDeleteFact(fact)">删除</el-button>
              </div>
            </div>
            <el-empty v-if="!factsLoading && !facts.length" description="暂无案件事实，请补充" />
          </div>
        </el-tab-pane>

        <!-- 3. 问诊记录 -->
        <el-tab-pane label="问诊记录" name="conversations">
          <div class="tab-actions" style="margin-bottom: 12px">
            <el-button type="primary" @click="startCaseConsult">发起案件问诊</el-button>
            <el-button @click="openLinkDialog">关联已有会话</el-button>
          </div>
          <el-table :data="conversations" v-loading="convLoading" stripe>
            <el-table-column prop="title" label="会话标题" min-width="240" show-overflow-tooltip />
            <el-table-column prop="status" label="会话状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ARCHIVED' ? 'info' : 'primary'" size="small" effect="plain">
                  {{ row.status === 'ARCHIVED' ? '已归档' : '进行中' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="会话创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="linkedAt" label="关联时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.linkedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="goChat(row)">查看会话</el-button>
              </template>
            </el-table-column>
            <template #empty><el-empty description="暂无关联问诊记录" /></template>
          </el-table>
        </el-tab-pane>

        <!-- 4. 证据材料 -->
        <el-tab-pane label="证据材料" name="evidence">
          <div class="evidence-toolbar">
            <el-input
              v-model="evFilters.evidenceName"
              placeholder="按证据名称查询"
              clearable
              style="width: 200px"
              @keyup.enter="searchEvidence"
            />
            <el-select v-model="evFilters.category" placeholder="证据分类" clearable style="width: 140px" @change="searchEvidence">
              <el-option v-for="c in EVIDENCE_CATEGORIES" :key="c" :label="c" :value="c" />
            </el-select>
            <el-select v-model="evFilters.ocrStatus" placeholder="识别状态" clearable style="width: 130px" @change="searchEvidence">
              <el-option label="等待处理" value="WAITING" />
              <el-option label="识别中" value="PROCESSING" />
              <el-option label="识别成功" value="SUCCESS" />
              <el-option label="识别失败" value="FAILED" />
            </el-select>
            <el-button type="primary" @click="searchEvidence">查询</el-button>
            <div class="toolbar-spacer" />
            <el-button type="primary" plain @click="openIntegrity">
              <el-icon class="btn-icon"><Compass /></el-icon>证据完整性分析
            </el-button>
            <el-button type="primary" @click="uploadDialogVisible = true">
              <el-icon class="btn-icon"><Upload /></el-icon>上传证据
            </el-button>
          </div>

          <el-table :data="evidences" v-loading="evLoading" stripe>
            <el-table-column prop="evidenceNo" label="证据编号" width="170" show-overflow-tooltip />
            <el-table-column prop="evidenceName" label="证据名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.fileType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column prop="ocrStatus" label="识别状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="OCR_STATUS_TAG[row.ocrStatus] || 'info'" size="small">
                  {{ OCR_STATUS_LABELS[row.ocrStatus] || row.ocrStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="上传时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEvidenceDetail(row)">详情</el-button>
                <el-button
                  link
                  type="warning"
                  :disabled="row.ocrStatus === 'PROCESSING' || row.ocrStatus === 'WAITING'"
                  @click="doRetryOcr(row)"
                >重新识别</el-button>
                <el-button link type="danger" @click="doDeleteEvidence(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty><el-empty description="暂无证据材料" /></template>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="evPageNum"
              v-model:page-size="evPageSize"
              :total="evTotal"
              :page-sizes="[5, 10, 20]"
              layout="total, sizes, prev, pager, next"
              background
            />
          </div>
        </el-tab-pane>
        <!-- 5. 证据清单 -->
        <el-tab-pane label="证据清单" name="checklist">
          <div v-if="missingItems.length" class="missing-alert">
            <el-alert type="warning" :closable="false" show-icon>
              <template #title>
                证据缺失提醒：还有 {{ missingItems.length }} 项建议收集的证据缺少对应材料 ——
                {{ missingItems.slice(0, 4).map((i) => i.evidenceName).join('、') }}
                {{ missingItems.length > 4 ? ' 等' : '' }}（以上为系统生成的收集建议，需结合案件实际情况判断）
              </template>
            </el-alert>
          </div>

          <div class="evidence-toolbar">
            <div class="toolbar-spacer" />
            <el-button @click="openAddChecklist">
              <el-icon class="btn-icon"><Plus /></el-icon>添加项目
            </el-button>
            <el-button type="primary" :loading="generating" @click="doGenerate">
              <el-icon class="btn-icon"><MagicStick /></el-icon>{{ checklist.length ? '重新生成清单' : '生成证据清单' }}
            </el-button>
          </div>

          <div v-if="checklistSummary" class="checklist-summary">
            <el-alert type="info" :closable="true" show-icon @close="checklistSummary = ''">
              <template #title>{{ checklistSummary }}</template>
            </el-alert>
          </div>

          <el-table :data="checklist" v-loading="checklistLoading" stripe>
            <el-table-column prop="evidenceName" label="证据名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip />
            <el-table-column prop="relatedFact" label="关联事实" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.relatedFact || '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="130" align="center">
              <template #default="{ row }">
                <el-select
                  :model-value="row.status"
                  size="small"
                  style="width: 108px"
                  @change="(v) => doChangeStatus(row, v)"
                >
                  <el-option
                    v-for="opt in CHECKLIST_STATUS_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="已关联证据" min-width="200">
              <template #default="{ row }">
                <template v-if="row.relatedEvidences && row.relatedEvidences.length">
                  <el-tag
                    v-for="ev in row.relatedEvidences"
                    :key="ev.id"
                    size="small"
                    closable
                    effect="plain"
                    style="margin-right: 6px"
                    @close="doUnrelate(row, ev.id)"
                  >{{ ev.evidenceName }}</el-tag>
                </template>
                <template v-else-if="row.suggestedEvidenceIds && row.suggestedEvidenceIds.length">
                  <span class="suggest-hint">
                    推荐关联：
                    <el-link
                      v-for="sid in row.suggestedEvidenceIds"
                      :key="sid"
                      type="primary"
                      style="margin-right: 4px; font-size: 12px; vertical-align: baseline"
                      @click="doRelate(row, sid)"
                    >{{ evidenceNameOf(sid) }}</el-link>
                  </span>
                </template>
                <span v-else class="analysis-hint">待上传</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRelate(row)">关联证据</el-button>
                <el-button link type="primary" @click="openEditChecklist(row)">编辑</el-button>
                <el-button link type="danger" @click="doDeleteChecklist(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无证据清单，点击右上角生成" />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 证据完整性分析弹窗 -->
    <el-dialog v-model="integrityVisible" title="证据完整性分析" width="680" top="6vh">
      <div v-loading="integrityLoading" class="integrity-body">
        <template v-if="integrity">
          <div class="section-title">整体说明</div>
          <div class="ocr-text pre-wrap">{{ integrity.summary || '（无）' }}</div>

          <template v-if="integrity.coveredFacts && integrity.coveredFacts.length">
            <div class="section-title">已有证据覆盖的事实</div>
            <div v-for="(c, i) in integrity.coveredFacts" :key="'c' + i" class="integrity-item">
              <div class="relation-content">{{ c.fact }}</div>
              <div class="analysis-hint">覆盖证据：{{ (c.evidences || []).join('、') || '-' }}</div>
            </div>
          </template>

          <template v-if="integrity.uncoveredFacts && integrity.uncoveredFacts.length">
            <div class="section-title">尚未覆盖的事实</div>
            <div v-for="(u, i) in integrity.uncoveredFacts" :key="'u' + i" class="integrity-item warn">
              {{ u }}
            </div>
          </template>

          <template v-if="integrity.suggestedMaterials && integrity.suggestedMaterials.length">
            <div class="section-title">建议补充的材料</div>
            <el-table :data="integrity.suggestedMaterials" size="small" border>
              <el-table-column prop="material" label="材料" min-width="150" />
              <el-table-column prop="purpose" label="用途" min-width="200" />
              <el-table-column prop="priority" label="优先级" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.priority === 'HIGH' ? 'danger' : row.priority === 'MEDIUM' ? 'warning' : 'info'" size="small">
                    {{ row.priority }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template v-if="integrity.toVerify && integrity.toVerify.length">
            <div class="section-title">需要进一步核实的信息</div>
            <div v-for="(v, i) in integrity.toVerify" :key="'v' + i" class="integrity-item warn">
              {{ v }}
            </div>
          </template>

          <div class="analysis-disclaimer">
            以上内容是根据当前案件信息生成的证据收集建议，仅供辅助整理，不能代表证据必然被法院采纳。
          </div>
        </template>
        <el-empty v-else-if="!integrityLoading" description="点击下方按钮开始分析" />
      </div>
      <template #footer>
        <el-button @click="integrityVisible = false">关闭</el-button>
        <el-button type="primary" :loading="integrityLoading" :disabled="!hasCaseFacts" @click="doAnalyzeIntegrity">
          {{ integrity ? '重新分析' : '开始分析' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 清单新增/编辑弹窗 -->
    <el-dialog
      v-model="checklistDialogVisible"
      :title="checklistEditId ? '编辑清单项目' : '添加清单项目'"
      width="520"
      :close-on-click-modal="false"
    >
      <el-form ref="checklistFormRef" :model="checklistForm" :rules="checklistRules" label-width="90px">
        <el-form-item label="证据名称" prop="evidenceName">
          <el-input v-model="checklistForm.evidenceName" maxlength="100" show-word-limit placeholder="如：劳动合同" />
        </el-form-item>
        <el-form-item label="证据用途">
          <el-input v-model="checklistForm.purpose" maxlength="200" placeholder="如：证明劳动关系存在" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="checklistForm.status" style="width: 100%">
            <el-option v-for="opt in CHECKLIST_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联事实">
          <el-input v-model="checklistForm.relatedFact" maxlength="200" placeholder="该证据支撑的案件事实（选填）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="checklistForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checklistDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="checklistSaving" @click="doSaveChecklist">保存</el-button>
      </template>
    </el-dialog>

    <!-- 关联证据弹窗 -->
    <el-dialog v-model="relateDialogVisible" title="关联实际证据" width="520">
      <div class="link-list">
        <div
          v-for="ev in evidences"
          :key="ev.id"
          class="link-item"
        >
          <div class="link-info">
            <div class="link-title">{{ ev.evidenceName }}</div>
            <div class="link-time">{{ ev.category || '未分类' }} · {{ OCR_STATUS_LABELS[ev.ocrStatus] || ev.ocrStatus }}</div>
          </div>
          <el-button
            size="small"
            :type="isRelatedToCurrent(ev.id) ? 'info' : 'primary'"
            plain
            @click="toggleRelate(ev)"
          >{{ isRelatedToCurrent(ev.id) ? '取消关联' : '关联' }}</el-button>
        </div>
        <el-empty v-if="!evidences.length" description="该案件暂无已上传证据" />
      </div>
    </el-dialog>

    <!-- 编辑案件弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑案件" width="560" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editForm" :rules="rules" label-width="90px">
        <el-form-item label="案件名称" prop="caseName">
          <el-input v-model="editForm.caseName" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="案件类型" prop="caseType">
          <el-select v-model="editForm.caseType" style="width: 100%">
            <el-option v-for="t in CASE_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="案件描述" prop="description">
          <el-input v-model="editForm.description" type="textarea" :rows="4" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="发生时间">
          <el-date-picker
            v-model="editForm.occurredAt"
            type="datetime"
            style="width: 100%"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="发生地点">
          <el-input v-model="editForm.location" />
        </el-form-item>
        <el-form-item label="涉及主体">
          <el-input v-model="editForm.partiesInvolved" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSaveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 关联会话弹窗 -->
    <el-dialog v-model="linkDialogVisible" title="关联已有问诊会话" width="560">
      <div v-loading="allConvLoading" class="link-list">
        <div v-for="conv in myConversations" :key="conv.id" class="link-item">
          <div class="link-info">
            <div class="link-title">{{ conv.title || '未命名会话' }}</div>
            <div class="link-time">{{ formatDateTime(conv.createdAt) }}</div>
          </div>
          <el-button
            size="small"
            type="primary"
            plain
            :disabled="linkedIds.has(conv.id)"
            @click="doLink(conv)"
          >{{ linkedIds.has(conv.id) ? '已关联' : '关联' }}</el-button>
        </div>
        <el-empty v-if="!allConvLoading && !myConversations.length" description="暂无历史问诊会话" />
      </div>
    </el-dialog>

    <!-- 上传证据弹窗 -->
    <el-dialog v-model="uploadDialogVisible" title="上传证据" width="520" :close-on-click-modal="false">
      <el-form :model="uploadForm" label-width="90px">
        <el-form-item label="证据文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :accept="EVIDENCE_ACCEPT"
            :on-change="onFileChange"
            :on-remove="() => (uploadForm.file = null)"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">
                支持 JPG/PNG/BMP/WEBP 图片、PDF、DOC、DOCX、TXT，单个文件不超过 {{ EVIDENCE_MAX_SIZE_MB }}MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="证据名称">
          <el-input v-model="uploadForm.evidenceName" placeholder="留空则使用文件名" maxlength="100" />
        </el-form-item>
        <el-form-item label="证据分类">
          <el-select v-model="uploadForm.category" style="width: 100%">
            <el-option v-for="c in EVIDENCE_CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="uploadForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!uploadForm.file" @click="doUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- 证据详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="证据详情" width="720" top="6vh">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border size="small">
          <el-descriptions-item label="证据编号">{{ detail.evidenceNo }}</el-descriptions-item>
          <el-descriptions-item label="原始文件">{{ detail.originalName }}</el-descriptions-item>
          <el-descriptions-item label="文件类型">{{ detail.fileType }}</el-descriptions-item>
          <el-descriptions-item label="识别状态">
            <el-tag :type="OCR_STATUS_TAG[detail.ocrStatus] || 'info'" size="small">
              {{ OCR_STATUS_LABELS[detail.ocrStatus] || detail.ocrStatus }}
            </el-tag>
            <span v-if="detail.ocrErrorMsg" class="ocr-error">{{ detail.ocrErrorMsg }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 智能证据分析（阶段三） -->
        <div class="section-title analysis-title">
          智能证据分析
          <el-tag
            v-if="analysisStatus"
            :type="ANALYSIS_STATUS_TAG[analysisStatus] || 'info'"
            size="small"
            style="margin-left: 8px"
          >{{ ANALYSIS_STATUS_LABELS[analysisStatus] || analysisStatus }}</el-tag>
        </div>
        <div v-if="!analysis && analysisStatus !== 'PROCESSING'" class="tab-actions" style="margin-top: 4px">
          <el-button
            type="primary"
            size="small"
            :disabled="!detail || detail.ocrStatus !== 'SUCCESS'"
            :loading="analyzing"
            @click="doAnalyze"
          >开始分析</el-button>
          <span v-if="detail && detail.ocrStatus !== 'SUCCESS'" class="analysis-hint">需先完成 OCR 识别</span>
        </div>
        <div v-loading="analysisLoading" class="analysis-body">
          <template v-if="analysis">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="证据类型">{{ analysis.evidenceType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="分析置信度">{{ analysis.confidence ?? '-' }} / 100</el-descriptions-item>
              <el-descriptions-item v-if="analysis.parties && analysis.parties.length" label="涉及主体" :span="2">
                <el-tag v-for="p in analysis.parties" :key="p" size="small" effect="plain" style="margin-right: 6px">{{ p }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>

            <div v-if="analysis.categoryRecommendation && analysis.categoryRecommendation.category" class="category-recommend">
              <span>分类建议：</span>
              <el-tag size="small" type="primary" effect="plain">{{ analysis.categoryRecommendation.category }}</el-tag>
              <span class="analysis-hint">置信度 {{ analysis.categoryRecommendation.confidence }}</span>
              <el-button
                v-if="analysis.categoryRecommendation.category !== detail?.category"
                size="small"
                type="primary"
                plain
                @click="doAcceptCategory"
              >采纳建议</el-button>
            </div>

            <template v-if="analysis.keyInformation && analysis.keyInformation.length">
              <div class="section-title">关键信息提取（均来自识别文本原文）</div>
              <el-table :data="analysis.keyInformation" size="small" border>
                <el-table-column prop="field" label="字段" width="130" />
                <el-table-column prop="value" label="提取值" min-width="160" show-overflow-tooltip />
                <el-table-column prop="sourceText" label="原文对应内容" min-width="220" show-overflow-tooltip />
              </el-table>
            </template>

            <template v-if="analysis.uncertainInformation && analysis.uncertainInformation.length">
              <div class="section-title">无法识别的信息（系统不会推测补全）</div>
              <div v-for="(u, i) in analysis.uncertainInformation" :key="i" class="uncertain-item">
                <el-tag size="small" type="warning" effect="plain">{{ u.field }}</el-tag>
                <span class="analysis-hint">{{ u.reason }}</span>
              </div>
            </template>

            <template v-if="analysis.relatedFacts && analysis.relatedFacts.length">
              <div class="section-title">关联案件事实（请确认或取消关联）</div>
              <div v-for="rel in analysis.relatedFacts" :key="rel.factId" class="relation-item">
                <div class="relation-content">{{ rel.factContent }}</div>
                <div class="relation-reason">关联依据：{{ rel.reason }}</div>
                <div class="relation-actions">
                  <el-tag v-if="rel.confirmed" size="small" type="success" effect="plain">已确认</el-tag>
                  <el-button v-else size="small" type="success" plain @click="doConfirmRelation(rel, true)">确认关联</el-button>
                  <el-button v-if="rel.confirmed" size="small" type="info" plain @click="doConfirmRelation(rel, false)">取消关联</el-button>
                </div>
              </div>
            </template>

            <div v-if="analysis.analysisResult" class="section-title">综合分析结论</div>
            <div v-if="analysis.analysisResult" class="ocr-text pre-wrap">{{ analysis.analysisResult }}</div>
            <div class="analysis-disclaimer">
              以上为系统辅助分析，不能认定证据真实性与法律效力，具体以原件和专业律师意见为准。
            </div>
          </template>
          <el-empty
            v-else-if="analysisStatus !== 'PROCESSING'"
            description="尚未分析"
            :image-size="60"
          />
        </div>

        <template v-if="detail && detail.ocrStatus === 'SUCCESS'">
          <div class="section-title">识别文本（原始，不可修改）</div>
          <div class="ocr-text pre-wrap">{{ detail.ocrText || '（识别结果为空）' }}</div>

          <div class="section-title">修正文本（可手动修正识别错误，不影响原始识别结果）</div>
          <el-input
            v-model="correctedText"
            type="textarea"
            :rows="6"
            placeholder="在此修正识别错误，保存后单独保存修正文本"
          />
          <div class="tab-actions">
            <el-button type="primary" :loading="savingCorrection" @click="doSaveCorrection">保存修正文本</el-button>
          </div>
        </template>
        <el-alert
          v-else-if="detail && detail.ocrStatus === 'FAILED'"
          type="error"
          :closable="false"
          show-icon
          title="识别失败"
          :description="detail.ocrErrorMsg || '请尝试重新识别'"
        />
        <el-alert
          v-else-if="detail"
          type="info"
          :closable="false"
          show-icon
          title="识别处理中"
          description="请稍后刷新查看识别结果"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import {
  getCase,
  updateCase,
  archiveCase,
  getCaseFacts,
  addCaseFact,
  deleteCaseFact,
  getCaseConversations,
  linkConversation
} from '@/api/case'
import {
  uploadEvidence,
  queryEvidences,
  getEvidence,
  updateEvidence,
  deleteEvidence,
  retryOcr,
  analyzeEvidence,
  getEvidenceAnalysis,
  updateRelation,
  analyzeIntegrity
} from '@/api/evidence'
import { listConversations } from '@/api/chat'
import {
  generateChecklist,
  getChecklist,
  createChecklistItem,
  updateChecklistItem,
  deleteChecklistItem,
  relateChecklistEvidence,
  unrelateChecklistEvidence
} from '@/api/checklist'

const route = useRoute()
const router = useRouter()
const caseId = Number(route.params.id)

// ===== 案件基本信息 =====
const loading = ref(false)
const caseInfo = ref(null)
const activeTab = ref('info')

async function loadCase() {
  loading.value = true
  try {
    const res = await getCase(caseId)
    if (res.code === 1) caseInfo.value = res.data
  } catch {
    // 错误已在拦截器提示（含无权访问）
  } finally {
    loading.value = false
  }
}
onMounted(loadCase)

// ===== 编辑案件 =====
const editDialogVisible = ref(false)
const saving = ref(false)
const editFormRef = ref()
const editForm = reactive({
  caseName: '',
  caseType: '',
  description: '',
  occurredAt: null,
  location: '',
  partiesInvolved: '',
  remark: ''
})
const rules = {
  caseName: [{ required: true, message: '请输入案件名称', trigger: 'blur' }],
  caseType: [{ required: true, message: '请选择案件类型', trigger: 'change' }],
  description: [{ required: true, message: '请输入案件描述', trigger: 'blur' }]
}

function openEdit() {
  Object.assign(editForm, {
    caseName: caseInfo.value.caseName,
    caseType: caseInfo.value.caseType,
    description: caseInfo.value.description || '',
    occurredAt: caseInfo.value.occurredAt,
    location: caseInfo.value.location || '',
    partiesInvolved: caseInfo.value.partiesInvolved || '',
    remark: caseInfo.value.remark || ''
  })
  editDialogVisible.value = true
}

function doSaveEdit() {
  editFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const res = await updateCase({ id: caseId, ...editForm })
      if (res.code === 1) {
        ElMessage.success('案件已更新')
        editDialogVisible.value = false
        await loadCase()
      }
    } catch {
      // 错误已在拦截器提示
    } finally {
      saving.value = false
    }
  })
}

async function doArchive() {
  try {
    await ElMessageBox.confirm('确定归档该案件吗？归档后历史数据全部保留。', '归档案件', { type: 'warning' })
  } catch {
    return
  }
  try {
    const res = await archiveCase(caseId)
    if (res.code === 1) {
      ElMessage.success('案件已归档')
      await loadCase()
    }
  } catch {
    // 忽略
  }
}

// ===== 案件事实 =====
const facts = ref([])
const factsLoading = ref(false)
const newFact = ref('')
const addingFact = ref(false)

async function loadFacts() {
  factsLoading.value = true
  try {
    const res = await getCaseFacts(caseId)
    if (res.code === 1) facts.value = res.data || []
  } catch {
    // 忽略
  } finally {
    factsLoading.value = false
  }
}

async function doAddFact() {
  if (!newFact.value.trim()) return
  addingFact.value = true
  try {
    const res = await addCaseFact(caseId, newFact.value.trim())
    if (res.code === 1) {
      ElMessage.success('事实已添加')
      newFact.value = ''
      await loadFacts()
    }
  } catch {
    // 忽略
  } finally {
    addingFact.value = false
  }
}

async function doDeleteFact(fact) {
  try {
    await ElMessageBox.confirm('确定删除该条案件事实吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    const res = await deleteCaseFact(fact.id)
    if (res.code === 1) {
      ElMessage.success('已删除')
      await loadFacts()
    }
  } catch {
    // 忽略
  }
}

// ===== 问诊记录 =====
const conversations = ref([])
const convLoading = ref(false)
const linkedIds = computed(() => new Set(conversations.value.map((c) => c.conversationId)))

async function loadConversations() {
  convLoading.value = true
  try {
    const res = await getCaseConversations(caseId)
    if (res.code === 1) conversations.value = res.data || []
  } catch {
    // 忽略
  } finally {
    convLoading.value = false
  }
}

// ===== 关联会话 =====
const linkDialogVisible = ref(false)
const allConvLoading = ref(false)
const myConversations = ref([])

async function openLinkDialog() {
  linkDialogVisible.value = true
  allConvLoading.value = true
  try {
    const res = await listConversations()
    if (res.code === 1) myConversations.value = res.data || []
  } catch {
    // 忽略
  } finally {
    allConvLoading.value = false
  }
}

async function doLink(conv) {
  try {
    const res = await linkConversation(caseId, conv.id)
    if (res.code === 1) {
      ElMessage.success('会话已关联')
      linkDialogVisible.value = false
      await Promise.all([loadConversations(), loadCase()])
    }
  } catch {
    // 错误已在拦截器提示（含已关联提示）
  }
}

function goChat(row) {
  // 带上业务会话 UUID 跳转到问诊页面（V1.0 已有按会话查看历史的能力）
  router.push({ path: '/consult', query: { conversationId: row.conversationUuid } })
}

/** V2.0（CHAT-001）：发起带案件上下文的问诊 */
function startCaseConsult() {
  router.push({ path: '/consult', query: { caseId } })
}

// ===== 证据材料 =====
const evFilters = reactive({ evidenceName: '', category: '', ocrStatus: '' })
const evidences = ref([])
const evLoading = ref(false)
const evPageNum = ref(1)
const evPageSize = ref(10)
const evTotal = ref(0)

let pollTimer = null

async function loadEvidences() {
  evLoading.value = true
  try {
    const res = await queryEvidences(
      { pageNum: evPageNum.value, pageSize: evPageSize.value },
      {
        caseId,
        evidenceName: evFilters.evidenceName || undefined,
        category: evFilters.category || undefined,
        ocrStatus: evFilters.ocrStatus || undefined
      }
    )
    if (res.code === 1) {
      evidences.value = res.data?.rows || []
      evTotal.value = res.data?.total || 0
      schedulePoll()
    }
  } catch {
    // 忽略
  } finally {
    evLoading.value = false
  }
}

function searchEvidence() {
  evPageNum.value = 1
  loadEvidences()
}

watch([evPageNum, evPageSize], () => loadEvidences())

/** 存在 WAITING/PROCESSING 状态证据时每 3 秒轮询，全部结束或超时后停止 */
function schedulePoll() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
  const processing = evidences.value.some(
    (e) => e.ocrStatus === 'WAITING' || e.ocrStatus === 'PROCESSING'
  )
  if (!processing) return
  pollTimer = setTimeout(() => {
    loadEvidences()
  }, 3000)
}

onMounted(loadEvidences)
onMounted(loadFacts)
onMounted(loadConversations)
onMounted(loadChecklist)
onBeforeUnmount(() => {
  if (pollTimer) clearTimeout(pollTimer)
})

// ===== 上传证据 =====
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadRef = ref()
const uploadForm = reactive({
  file: null,
  evidenceName: '',
  category: '其他材料',
  remark: ''
})

function onFileChange(file) {
  uploadForm.file = file.raw
}

async function doUpload() {
  if (!uploadForm.file) return
  uploading.value = true
  try {
    const res = await uploadEvidence({
      caseId,
      file: uploadForm.file,
      evidenceName: uploadForm.evidenceName,
      category: uploadForm.category,
      remark: uploadForm.remark
    })
    if (res.code === 1) {
      ElMessage.success('证据上传成功，正在异步识别')
      uploadDialogVisible.value = false
      Object.assign(uploadForm, { file: null, evidenceName: '', remark: '' })
      uploadRef.value?.clearFiles()
      searchEvidence()
    }
  } catch {
    // 错误已在拦截器提示
  } finally {
    uploading.value = false
  }
}

// ===== 证据操作 =====
async function doRetryOcr(row) {
  try {
    const res = await retryOcr(row.id)
    if (res.code === 1) {
      ElMessage.success('已触发重新识别')
      await loadEvidences()
    }
  } catch {
    // 忽略
  }
}

async function doDeleteEvidence(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除证据「${row.evidenceName}」吗？识别文本与本地文件将一并删除。`,
      '删除证据',
      { type: 'error', confirmButtonText: '确认删除' }
    )
  } catch {
    return
  }
  try {
    const res = await deleteEvidence(row.id)
    if (res.code === 1) {
      ElMessage.success('证据已删除')
      await loadEvidences()
      await loadCase()
    }
  } catch {
    // 忽略
  }
}

// ===== 证据详情 =====
const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const correctedText = ref('')
const savingCorrection = ref(false)

async function openEvidenceDetail(row) {
  detailDialogVisible.value = true
  detailLoading.value = true
  detail.value = null
  analysis.value = null
  analysisStatus.value = null
  try {
    const res = await getEvidence(row.id)
    if (res.code === 1) {
      detail.value = res.data
      correctedText.value = res.data?.correctedText || ''
      analysisStatus.value = res.data?.analysisStatus || null
      // 已有分析结果则直接加载展示
      if (analysisStatus.value === 'SUCCESS') {
        await loadAnalysis()
      }
    }
  } catch {
    // 忽略
  } finally {
    detailLoading.value = false
  }
}

// ===== 智能证据分析（阶段三） =====
const analysis = ref(null)
const analysisStatus = ref(null)
const analysisLoading = ref(false)
const analyzing = ref(false)

async function loadAnalysis() {
  analysisLoading.value = true
  try {
    const res = await getEvidenceAnalysis(detail.value.id)
    if (res.code === 1) {
      analysis.value = res.data
      if (res.data) analysisStatus.value = 'SUCCESS'
    }
  } catch {
    // 忽略
  } finally {
    analysisLoading.value = false
  }
}

async function doAnalyze() {
  if (!detail.value) return
  analyzing.value = true
  analysisStatus.value = 'PROCESSING'
  try {
    const res = await analyzeEvidence(detail.value.id)
    if (res.code === 1) {
      ElMessage.success('已触发智能分析，请稍候')
      // 轮询分析状态，直到完成或超时（LLM 分析一般 10-40 秒）
      let tries = 0
      const timer = setInterval(async () => {
        tries++
        try {
          const statusRes = await getEvidence(detail.value.id)
          const st = statusRes.data?.analysisStatus
          if (st === 'SUCCESS') {
            clearInterval(timer)
            analysisStatus.value = 'SUCCESS'
            detail.value.analysisStatus = 'SUCCESS'
            await loadAnalysis()
          } else if (st === 'FAILED' || tries > 40) {
            clearInterval(timer)
            analysisStatus.value = st === 'FAILED' ? 'FAILED' : 'FAILED'
            if (st !== 'FAILED') ElMessage.error('分析超时，请稍后重试')
          }
        } catch {
          clearInterval(timer)
          analysisStatus.value = 'FAILED'
        }
      }, 3000)
    } else {
      analysisStatus.value = 'FAILED'
    }
  } catch {
    analysisStatus.value = 'FAILED'
  } finally {
    analyzing.value = false
  }
}

/** 采纳分类建议（低置信度建议由用户自行判断，此处为用户主动采纳） */
async function doAcceptCategory() {
  if (!analysis.value?.categoryRecommendation) return
  try {
    const res = await updateEvidence({
      id: detail.value.id,
      category: analysis.value.categoryRecommendation.category
    })
    if (res.code === 1) {
      ElMessage.success('已采纳分类建议')
      detail.value.category = analysis.value.categoryRecommendation.category
      await loadEvidences()
    }
  } catch {
    // 忽略
  }
}

/** 确认/取消证据与案件事实的关联 */
async function doConfirmRelation(rel, confirmed) {
  try {
    const res = await updateRelation(detail.value.id, rel.factId, confirmed)
    if (res.code === 1) {
      ElMessage.success(confirmed ? '已确认关联' : '已取消关联')
      if (res.data) analysis.value = res.data
    }
  } catch {
    // 忽略
  }
}

// ===== 证据完整性分析 =====
const integrityVisible = ref(false)
const integrityLoading = ref(false)
const integrity = ref(null)
const hasCaseFacts = computed(() => facts.value.length > 0)

function openIntegrity() {
  integrityVisible.value = true
  if (!integrity.value) {
    doAnalyzeIntegrity()
  }
}

async function doAnalyzeIntegrity() {
  integrityLoading.value = true
  try {
    const res = await analyzeIntegrity(caseId)
    if (res.code === 1) integrity.value = res.data
  } catch {
    // 错误已在拦截器提示
  } finally {
    integrityLoading.value = false
  }
}

// ===== 证据清单（阶段四） =====
const checklist = ref([])
const checklistLoading = ref(false)
const generating = ref(false)
const checklistSummary = ref('')

const missingItems = computed(() =>
  checklist.value.filter(
    (i) => i.status === CHECKLIST_STATUS.REQUIRED && (!i.relatedEvidences || !i.relatedEvidences.length)
  )
)

function evidenceNameOf(id) {
  const ev = evidences.value.find((e) => e.id === id)
  return ev ? ev.evidenceName : `证据#${id}`
}

async function loadChecklist() {
  checklistLoading.value = true
  try {
    const res = await getChecklist(caseId)
    if (res.code === 1) checklist.value = res.data || []
  } catch {
    // 忽略
  } finally {
    checklistLoading.value = false
  }
}

async function doGenerate() {
  if (checklist.value.length) {
    try {
      await ElMessageBox.confirm(
        '重新生成将结合最新案件情况输出清单，已有项目（含已关联证据的项目）会保留，同名项目不会重复添加。继续吗？',
        '重新生成清单',
        { type: 'info' }
      )
    } catch {
      return
    }
  }
  generating.value = true
  try {
    const res = await generateChecklist(caseId)
    if (res.code === 1) {
      ElMessage.success('证据清单已生成')
      checklistSummary.value = res.msg || ''
      await loadChecklist()
    }
  } catch {
    // 错误已在拦截器提示
  } finally {
    generating.value = false
  }
}

// —— 新增 / 编辑 ——
const checklistDialogVisible = ref(false)
const checklistSaving = ref(false)
const checklistEditId = ref(null)
const checklistFormRef = ref()
const checklistForm = reactive({
  evidenceName: '',
  purpose: '',
  status: CHECKLIST_STATUS.REQUIRED,
  relatedFact: '',
  remark: ''
})
const checklistRules = {
  evidenceName: [{ required: true, message: '请输入证据名称', trigger: 'blur' }]
}

function openAddChecklist() {
  checklistEditId.value = null
  Object.assign(checklistForm, {
    evidenceName: '',
    purpose: '',
    status: CHECKLIST_STATUS.REQUIRED,
    relatedFact: '',
    remark: ''
  })
  checklistDialogVisible.value = true
}

function openEditChecklist(row) {
  checklistEditId.value = row.id
  Object.assign(checklistForm, {
    evidenceName: row.evidenceName,
    purpose: row.purpose || '',
    status: row.status,
    relatedFact: row.relatedFact || '',
    remark: row.remark || ''
  })
  checklistDialogVisible.value = true
}

function doSaveChecklist() {
  checklistFormRef.value.validate(async (valid) => {
    if (!valid) return
    checklistSaving.value = true
    try {
      if (checklistEditId.value) {
        const res = await updateChecklistItem({ id: checklistEditId.value, ...checklistForm })
        if (res.code === 1) ElMessage.success('清单项目已更新')
      } else {
        const res = await createChecklistItem({ caseId, ...checklistForm })
        if (res.code === 1) ElMessage.success('清单项目已添加')
      }
      checklistDialogVisible.value = false
      await loadChecklist()
    } catch {
      // 忽略
    } finally {
      checklistSaving.value = false
    }
  })
}

async function doChangeStatus(row, status) {
  try {
    const res = await updateChecklistItem({ id: row.id, status })
    if (res.code === 1) {
      row.status = status
      ElMessage.success('状态已更新')
    }
  } catch {
    // 忽略
  }
}

async function doDeleteChecklist(row) {
  try {
    await ElMessageBox.confirm(`确定删除清单项目「${row.evidenceName}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    const res = await deleteChecklistItem(row.id)
    if (res.code === 1) {
      ElMessage.success('已删除')
      await loadChecklist()
    }
  } catch {
    // 忽略
  }
}

// —— 关联证据 ——
const relateDialogVisible = ref(false)
const relateTarget = ref(null)

function isRelatedToCurrent(evidenceId) {
  return Boolean(
    relateTarget.value?.relatedEvidences?.some((r) => r.id === evidenceId)
  )
}

function openRelate(row) {
  relateTarget.value = row
  relateDialogVisible.value = true
}

async function toggleRelate(ev) {
  if (!relateTarget.value) return
  if (isRelatedToCurrent(ev.id)) {
    await doUnrelate(relateTarget.value, ev.id)
  } else {
    await doRelate(relateTarget.value, ev.id)
  }
}

async function doRelate(row, evidenceId) {
  try {
    const res = await relateChecklistEvidence(row.id, evidenceId)
    if (res.code === 1) {
      ElMessage.success('已关联')
      if (relateTarget.value?.id === row.id) relateDialogVisible.value = false
      await loadChecklist()
    }
  } catch {
    // 忽略（含重复关联提示）
  }
}

async function doUnrelate(row, evidenceId) {
  try {
    const res = await unrelateChecklistEvidence(row.id, evidenceId)
    if (res.code === 1) {
      ElMessage.success('已取消关联')
      await loadChecklist()
    }
  } catch {
    // 忽略
  }
}

async function doSaveCorrection() {
  if (!detail.value) return
  savingCorrection.value = true
  try {
    const res = await updateEvidence({ id: detail.value.id, correctedText: correctedText.value })
    if (res.code === 1) {
      ElMessage.success('修正文本已保存')
      detail.value.correctedText = correctedText.value
      detailDialogVisible.value = false
    }
  } catch {
    // 忽略
  } finally {
    savingCorrection.value = false
  }
}
</script>

<style scoped>
.page {
  padding: 20px 24px;
  height: 100%;
  overflow-y: auto;
}
.header {
  margin-bottom: 16px;
}
.header-main {
  display: flex;
  align-items: center;
  gap: 10px;
}
.title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}
.case-name {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-primary);
}
.case-no {
  font-size: 12px;
  color: var(--color-text-muted);
}
.card {
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 8px 20px 20px;
}
.desc {
  margin-top: 8px;
}
.pre-wrap {
  white-space: pre-wrap;
  word-break: break-all;
}
.tab-actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
}
.fact-add {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
  margin-bottom: 16px;
}
.fact-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.fact-item {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  padding: 12px 14px;
  background: var(--color-bg);
}
.fact-content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.fact-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.fact-time {
  font-size: 12px;
  color: var(--color-text-muted);
}
.evidence-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.toolbar-spacer {
  flex: 1;
}
.btn-icon {
  margin-right: 4px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.link-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 380px;
  overflow-y: auto;
}
.link-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
}
.link-title {
  font-size: 14px;
  color: var(--color-text);
}
.link-time {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 2px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin: 16px 0 8px;
}
.ocr-text {
  background: var(--color-bg);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.7;
  max-height: 260px;
  overflow-y: auto;
}
.ocr-error {
  margin-left: 8px;
  font-size: 12px;
  color: var(--color-danger);
}
.analysis-title {
  margin-top: 22px;
}
.analysis-hint {
  font-size: 12px;
  color: var(--color-text-muted);
}
.analysis-body {
  min-height: 60px;
}
.category-recommend {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  font-size: 13px;
}
.uncertain-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.relation-item {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  margin-bottom: 8px;
  background: var(--color-bg);
}
.relation-content {
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.relation-reason {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}
.relation-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.analysis-disclaimer {
  margin-top: 12px;
  font-size: 12px;
  color: var(--color-text-muted);
  border-top: 1px dashed var(--color-border-light);
  padding-top: 8px;
}
.integrity-body {
  min-height: 120px;
}
.integrity-item {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  margin-bottom: 8px;
  font-size: 13px;
}
.integrity-item.warn {
  background: var(--color-accent-bg);
}
.missing-alert {
  margin-bottom: 12px;
}
.checklist-summary {
  margin-bottom: 12px;
}
.suggest-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
