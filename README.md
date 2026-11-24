# Iron HUD

NeoForge **1.21.1** 클라이언트 전용 모드 — 영화 속 파워슈트 HUD에서 영감을 받은 SF 헬멧 디스플레이.
서버에 설치할 필요가 없고, 일반/바닐라 서버에도 접속할 수 있습니다.

> 팬 프로젝트입니다. 영화사 및 원작 권리자와 무관하며, 로고·영상·음원·폰트 등 원본 자산을 일절 사용하지 않습니다.
> 효과음 10종은 전부 코드로 합성한 오리지널입니다 (`tools/gen_sounds.py`).

## 기능

| 모듈 | 설명 |
|---|---|
| 아크 리액터 | 체력·허기(전력)·방어력 3중 게이지, 체력 낮으면 적색 맥동, 산소 바 |
| **슈트 상태 도면** | 투구/흉갑/레깅스/부츠 내구도를 인체 도면에 색상으로 표시. 피격 시 해당 부위 점멸, 내구도 30% 미만 적색 경고, 파손 시 `ARMOR BREACH` |
| **파손 연출** | 큰 피해를 받으면 바이저에 균열 생성(시간이 지나면 사라짐), 체력 30% 이하에서는 균열이 유지되고 화면 가장자리가 붉게 맥동 |
| 경고 배너 | 위험도별(정보/주의/치명) 배너와 효과음: 선체 손상, 전력 부족, 산소 부족, 화염, 용암 근접, 적 근접, 크리퍼 점화, 갑옷 파손 등 |
| **충돌/추락 경고** | 겉날개 비행·낙하 중 진행 방향을 레이캐스트해 충돌까지 시간(TTC) 표시, 낙하 피해 예측, 치명적이면 `FATAL IMPACT` |
| 인공 수평선 | 비행 중 피치 래더 + 비행경로 마커 (설정으로 상시 표시 가능) |
| 나침반 | 방위 테이프, 주변 적 위치 틱, 웨이포인트 마커 |
| 레이더 | 평면 지도: 적(적색)/플레이어/중립 개체, 스윕 애니메이션, 웨이포인트 |
| **시네마틱 타깃 스캔** | 바라본 개체에 회전 링 스택이 달라붙고(체력 호 포함), 와이어프레임 박스 + 위아래로 훑는 스캔 평면, 리더 라인에서 마크 정보가 타이핑되듯 출력: 종류/분류/체력/방어/공격력/속도/거리/좌표/크기/상태이상/손에 든 아이템/위협도. 처치 시 `TARGET ELIMINATED` |
| 월드 마커 | 적대 몹 위에 삼각 마커 + 거리, 웨이포인트 3D 마커 |
| 광학 줌 | 키 누르고 휠로 배율 조절, 줌 전용 레티클 |
| 광역 스캔 | 화면 전체로 퍼지는 원형 펄스, 펄스가 닿는 개체마다 회전 링 + 이름·거리·체력바 |
| 고도 그래프 | 최근 고도 변화 트레이스, 상승/하강 속도, 지면까지 거리(AGL) |
| 효과음 | 부팅, 종료, 주의, 치명 경고(클랙슨), 갑옷 파손, 락온, 처치, 충돌, 줌, 핑 |

## 조작 (조작 설정 > "Iron HUD"에서 변경 가능)

| 키 | 동작 |
|---|---|
| `H` | HUD 켜기/끄기 (켤 때 부팅 시퀀스 재생) |
| `Z` (누르고 있기) | 광학 줌 — 마우스 휠로 배율 조절 |
| `K` | 현재 위치에 웨이포인트 설정 (6블록 이내에서 다시 누르면 해제) |
| `X` | 광역 스캔 펄스 |

설정은 모드 목록 > Iron HUD > 설정 화면 또는 `config/ironhud-client.toml`에서 바꿀 수 있습니다
(색상 테마, 효과음/볼륨, 각 모듈 표시, 락온/레이더 거리, 최대 줌 등).

## 설치

[Releases](../../releases) 또는 Actions 아티팩트의 `ironhud-*.jar`를 `mods` 폴더에 넣으세요.
필요: Minecraft 1.21.1 + NeoForge 21.1.x

## 빌드

JDK 21, Gradle 8.8+ 필요.

```
gradle build          # build/libs/ironhud-1.0.0.jar
gradle runClient      # 개발용 클라이언트
```

`main`에 push하면 GitHub Actions가 자동으로 빌드하고, `v*` 태그를 push하면 Release에 jar를 첨부합니다.

## 효과음 다시 만들기

```
python3 tools/gen_sounds.py    # numpy + ffmpeg(libvorbis) 필요
```

## 영화 HUD 참고 자료

기능 구성은 영화 속 HUD를 분석한 공개 글을 참고했습니다 (기능 목록만 참고, 자산 사용 없음):

- [Sci-fi Interfaces — Iron Man HUD: Just the functions](https://scifiinterfaces.com/2015/07/13/iron-man-hud-just-the-functions/)
- [AWN — Graphic UIs in Avengers: Age of Ultron](https://www.awn.com/vfxworld/keeping-it-user-friendly-graphic-uis-avengers-age-ultron)
- [Creative Bloq — The Cinema 4D secrets of Iron Man's interface](https://www.creativebloq.com/3d/cinema-4d-secrets-iron-mans-interface-61515419)

## 라이선스

MIT
