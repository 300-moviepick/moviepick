package com.moviepick.domain.report.service;

import com.moviepick.domain.report.dto.request.AdminReportProcessRequest;
import com.moviepick.domain.report.repository.AdminSanctionRepository;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.report.entity.CommunityReport;
import com.moviepick.domain.report.entity.ReportStatus;
import com.moviepick.domain.report.entity.ReportTargetType;
import com.moviepick.domain.report.repository.CommunityReportRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.report.entity.AdminSanction;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("concurrency")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminReportConcurrencyTest {
    @Autowired
    private AdminReportService adminReportService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommunityReportRepository communityReportRepository;

    @Autowired
    private AdminSanctionRepository adminSanctionRepository;

    private static final AdminReportProcessRequest ACCEPT =
            new AdminReportProcessRequest(ReportStatus.ACCEPTED);

    private Member saveMember(String nickname) {
        return memberRepository.save(
                Member.createLocal(
                        nickname + "@test.com",
                        "encoded",
                        nickname
                )
        );
    }

    private CommunityReport saveReport(Member reporter, Post post) {
        return communityReportRepository.save(
                CommunityReport.create(
                        reporter,
                        ReportTargetType.POST,
                        post.getId(),
                        "SPAM",
                        null
                )
        );
    }
    @Test
    void differentPostsSameWriterConcurrentApproval() throws Exception {

        // 1. 같은 작성자의 서로 다른 게시글 2개 생성
        Member writer = saveMember("writer");
        Post post1 = postRepository.save(
                Post.create(writer, "title1", "content1")
        );
        Post post2 = postRepository.save(
                Post.create(writer, "title2", "content2")
        );

        // 2. 각 게시글에 신고 생성
        Long reportId1 = saveReport(
                saveMember("reporter1"), post1
        ).getId();

        Long reportId2 = saveReport(
                saveMember("reporter2"), post2
        ).getId();

        // 3. 두 스레드가 동시에 신고 승인 시작
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Void> task1 = () -> {
                ready.countDown();
                start.await();
                adminReportService.processReport(reportId1, ACCEPT);
                return null;
            };

            Callable<Void> task2 = () -> {
                ready.countDown();
                start.await();
                adminReportService.processReport(reportId2, ACCEPT);
                return null;
            };

            Future<Void> future1 = executor.submit(task1);
            Future<Void> future2 = executor.submit(task2);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            // 예외가 발생하면 테스트 실패
            future1.get(15, TimeUnit.SECONDS);
            future2.get(15, TimeUnit.SECONDS);

            // 4. 같은 회원에게 제재가 정확히 2건 적용됐는지 확인
            assertThat(
                    adminSanctionRepository.countByMemberId(writer.getId())
            ).isEqualTo(2);

            // 5. 두 신고 모두 승인됐는지 확인
            assertThat(
                    communityReportRepository.findById(reportId1)
                            .orElseThrow().getStatus()
            ).isEqualTo(ReportStatus.ACCEPTED);

            assertThat(
                    communityReportRepository.findById(reportId2)
                            .orElseThrow().getStatus()
            ).isEqualTo(ReportStatus.ACCEPTED);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        // 6. 제재 이력 조회
        List<AdminSanction> sanctions =
                adminSanctionRepository.findAllByMemberId(writer.getId());

        // 7. 누적 제재 차수가 1차, 2차인지 검증
        assertThat(sanctions)
                .extracting(AdminSanction::getSanctionCount)
                .containsExactlyInAnyOrder(1, 2);

        // 8. 각 제재의 정지 기간 검증
        for (AdminSanction sanction : sanctions) {

            int count = sanction.getSanctionCount();

            LocalDateTime suspendedUntil =
                    sanction.getSuspendedUntil();

            if (count == 1) {
                // 1차 제재: 1일 정지
                assertThat(suspendedUntil).isNotNull();
                assertThat(suspendedUntil)
                        .isBetween(
                                LocalDateTime.now().plusDays(1).minusMinutes(5),
                                LocalDateTime.now().plusDays(1).plusMinutes(5)
                        );

            } else if (count == 2) {
                // 2차 제재: 7일 정지
                assertThat(suspendedUntil).isNotNull();
                assertThat(suspendedUntil)
                        .isBetween(
                                LocalDateTime.now().plusDays(7).minusMinutes(5),
                                LocalDateTime.now().plusDays(7).plusMinutes(5)
                        );
            }

        }


    }

    @Test
    void sameReportConcurrentApproval() throws Exception {

        // 1. 신고 대상 작성자 및 게시글 생성
        Member writer = saveMember("sameWriter");

        Post post = postRepository.save(
                Post.create(writer, "sameTitle", "sameContent")
        );

        // 2. 신고 1건 생성
        Long reportId = saveReport(
                saveMember("sameReporter"), post
        ).getId();

        // 3. 같은 신고를 두 스레드가 동시에 승인
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Boolean> task = () -> {
                ready.countDown();
                start.await();

                try {
                    adminReportService.processReport(reportId, ACCEPT);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };

            Future<Boolean> future1 = executor.submit(task);
            Future<Boolean> future2 = executor.submit(task);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean result1 = future1.get(15, TimeUnit.SECONDS);
            boolean result2 = future2.get(15, TimeUnit.SECONDS);

            // 4. 정확히 한 요청만 성공해야 함
            assertThat(result1 != result2).isTrue();

            // 5. 제재 이력은 정확히 1건이어야 함
            assertThat(
                    adminSanctionRepository.countByMemberId(writer.getId())
            ).isEqualTo(1);

            // 6. 신고는 승인 상태여야 함
            assertThat(
                    communityReportRepository.findById(reportId)
                            .orElseThrow()
                            .getStatus()
            ).isEqualTo(ReportStatus.ACCEPTED);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void samePostDifferentReportsConcurrentApproval() throws Exception {

        // 1. 게시글 작성자 생성
        Member writer = saveMember("samePostWriter");

        // 2. 게시글 하나 생성
        Post post = postRepository.save(
                Post.create(writer, "samePostTitle", "samePostContent")
        );

        // 3. 같은 게시글에 서로 다른 신고 2건 생성
        Long reportId1 = saveReport(
                saveMember("samePostReporter1"), post
        ).getId();

        Long reportId2 = saveReport(
                saveMember("samePostReporter2"), post
        ).getId();

        // 4. 두 스레드가 동시에 서로 다른 신고 승인 요청
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Boolean> task1 = () -> {
                ready.countDown();
                start.await();

                try {
                    adminReportService.processReport(reportId1, ACCEPT);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };

            Callable<Boolean> task2 = () -> {
                ready.countDown();
                start.await();

                try {
                    adminReportService.processReport(reportId2, ACCEPT);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };

            Future<Boolean> future1 = executor.submit(task1);
            Future<Boolean> future2 = executor.submit(task2);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean result1 = future1.get(15, TimeUnit.SECONDS);
            boolean result2 = future2.get(15, TimeUnit.SECONDS);

            // 5. 두 요청 중 정확히 하나만 성공
            assertThat(result1 != result2).isTrue();

            // 6. 같은 게시글에 대한 제재는 정확히 1건
            assertThat(
                    adminSanctionRepository.countByMemberId(writer.getId())
            ).isEqualTo(1);

            // 7. 두 신고 모두 승인 상태
            assertThat(
                    communityReportRepository.findById(reportId1)
                            .orElseThrow()
                            .getStatus()
            ).isEqualTo(ReportStatus.ACCEPTED);

            assertThat(
                    communityReportRepository.findById(reportId2)
                            .orElseThrow()
                            .getStatus()
            ).isEqualTo(ReportStatus.ACCEPTED);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void sameReportConcurrentAcceptAndReject() throws Exception {

        // 1. 게시글 작성자와 신고 생성
        Member writer = saveMember("acceptRejectWriter");

        Post post = postRepository.save(
                Post.create(writer, "title", "content")
        );

        Long reportId = saveReport(
                saveMember("acceptRejectReporter"), post
        ).getId();

        AdminReportProcessRequest reject =
                new AdminReportProcessRequest(ReportStatus.REJECTED);

        // 2. 승인과 반려를 동시에 실행
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Boolean> acceptTask = () -> {
                ready.countDown();
                start.await();

                try {
                    adminReportService.processReport(reportId, ACCEPT);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };

            Callable<Boolean> rejectTask = () -> {
                ready.countDown();
                start.await();

                try {
                    adminReportService.processReport(reportId, reject);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };

            Future<Boolean> acceptFuture = executor.submit(acceptTask);
            Future<Boolean> rejectFuture = executor.submit(rejectTask);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean acceptSuccess = acceptFuture.get(15, TimeUnit.SECONDS);
            boolean rejectSuccess = rejectFuture.get(15, TimeUnit.SECONDS);

            // 3. 승인과 반려 중 정확히 하나만 성공
            assertThat(acceptSuccess != rejectSuccess).isTrue();

            // 4. 최종 신고 상태 확인
            ReportStatus finalStatus =
                    communityReportRepository.findById(reportId)
                            .orElseThrow()
                            .getStatus();

            if (acceptSuccess) {
                // 승인이 성공했다면 제재 1건
                assertThat(finalStatus).isEqualTo(ReportStatus.ACCEPTED);

                assertThat(
                        adminSanctionRepository.countByMemberId(writer.getId())
                ).isEqualTo(1);

            } else {
                // 반려가 성공했다면 제재 0건
                assertThat(finalStatus).isEqualTo(ReportStatus.REJECTED);

                assertThat(
                        adminSanctionRepository.countByMemberId(writer.getId())
                ).isZero();
            }

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }




}
