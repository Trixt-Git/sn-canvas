/**
 * The toolbar's pen weight toggle (Wil's fork): one tap switches between Fine and Bold and takes up the pencil,
 * and the buttons after it are measured one slot further along, so the hints still point at them.
 */
import React from 'react';
import ReactTestRenderer, {act} from 'react-test-renderer';
import type {PenWeightId} from '../src/domain/styles';
import Toolbar, {TOOLBAR_GEOMETRY} from '../src/ui/Toolbar';
import type {ToolMode} from '../src/ui/nativeCanvasView';

const renderToolbar = (toolMode: ToolMode, penWeight: PenWeightId) => {
  const onToolChange = jest.fn();
  const onPenWeightChange = jest.fn();
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  act(() => {
    renderer = ReactTestRenderer.create(
      <Toolbar
        toolMode={toolMode}
        onToolChange={onToolChange}
        onInsertImage={jest.fn()}
        showHints={false}
        onToggleHints={jest.fn()}
        onClearCanvas={jest.fn()}
        canClearCanvas={false}
        penWeight={penWeight}
        onPenWeightChange={onPenWeightChange}
      />,
    );
  });
  const toggle = () => renderer.root.findByProps({testID: 'canvas-pen-weight'});
  const press = () =>
    act(() => {
      toggle().props.onPress();
    });
  return {onToolChange, onPenWeightChange, toggle, press};
};

test('the toggle says which weight the pencil writes at', () => {
  expect(renderToolbar('draw', 'fine').toggle().props.accessibilityLabel).toBe('Pen weight: fine');
  expect(renderToolbar('draw', 'bold').toggle().props.accessibilityLabel).toBe('Pen weight: bold');
});

test('a tap switches to the other weight, and takes up the pencil if it was not in hand', () => {
  const writing = renderToolbar('draw', 'fine');
  writing.press();
  expect(writing.onPenWeightChange).toHaveBeenCalledWith('bold');
  expect(writing.onToolChange).not.toHaveBeenCalled();

  const selecting = renderToolbar('select', 'bold');
  selecting.press();
  expect(selecting.onPenWeightChange).toHaveBeenCalledWith('fine');
  expect(selecting.onToolChange).toHaveBeenCalledWith('draw');
});

test('the toggle sits straight after the pencil, and the eraser one slot further on', () => {
  const slot = TOOLBAR_GEOMETRY.toolCenterX('rectangle') - TOOLBAR_GEOMETRY.toolCenterX('select');
  expect(TOOLBAR_GEOMETRY.penWeightCenterX - TOOLBAR_GEOMETRY.toolCenterX('draw')).toBeCloseTo(slot);
  expect(TOOLBAR_GEOMETRY.toolCenterX('eraser') - TOOLBAR_GEOMETRY.penWeightCenterX).toBeCloseTo(slot);
});
