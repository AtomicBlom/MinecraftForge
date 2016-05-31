package net.minecraftforge.common.plon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import static net.minecraftforge.common.plon.AST.*;

/**
 * Created by rainwarrior on 5/18/16.
 */
public abstract class Interpreter
{
    // common static stuff

    private static final ISExp labelSymbol = makeSymbol("&labeled");
    private static final ISExp functionSymbol = makeSymbol("&function");
    private static final ISExp macroSymbol = makeSymbol("&macro");
    static final ISExp mapSymbol = makeSymbol("&map");

    private static ISExp makeFunction(IList args, ISExp body, IList env)
    {
        return new Cons(functionSymbol, new Cons(args, new Cons(body, new Cons(env, Nil.INSTANCE))));
    }
    
    // common stuff

    // TODO in dis
    private Unifier unifier;

    private final Map readFrame = new Map(ImmutableMap.of(makeSymbol("load"), new Load()
    {
        @Override
        protected ISExp read(String location)
        {
            return Interpreter.this.read(location);
        }
    }));

    protected abstract ISExp read(String location);

    public ISExp eval(ISExp exp, ImmutableMap<? extends ISExp, ? extends ISExp> topEnv)
    {
        this.unifier = null;
        return eval(exp, new Cons(new Map(topEnv), new Cons(readFrame, new Cons(new Map(PrimOp.values), Nil.INSTANCE))));
    }

    public ISExp eval(ISExp exp)
    {
        this.unifier = null;
        return eval(exp, new Cons(readFrame, new Cons(new Map(PrimOp.values), Nil.INSTANCE)));
    }

    public ISExp infer(ISExp exp, ImmutableMap<? extends ISExp, ? extends ISExp> topEnv, Unifier unifier)
    {
        this.unifier = unifier;
        return infer(exp, envToContext(new Cons(new Map(topEnv), new Cons(readFrame, new Cons(new Map(PrimOp.values), Nil.INSTANCE))), ImmutableSet.<ISExp>of()), ImmutableSet.<ISExp>of());
    }

    public ISExp infer(ISExp exp, Unifier unifier)
    {
        this.unifier = unifier;
        return infer(exp, envToContext(new Cons(readFrame, new Cons(new Map(PrimOp.values), Nil.INSTANCE)), ImmutableSet.<ISExp>of()), ImmutableSet.<ISExp>of());
    }

    private IList evlis(IList list, IList env)
    {
        if (list == Nil.INSTANCE)
        {
            return Nil.INSTANCE;
        }
        Cons cons = (Cons) list;
        return new Cons(eval(cons.car, env), evlis(cons.cdr, env));
    }

    private IList reilist(IList args, java.util.Map<ISExp, ISExp> newVars, ImmutableSet<ISExp> boundVarTypes)
    {
        if(args == Nil.INSTANCE)
        {
            return Nil.INSTANCE;
        }
        Cons cons = (Cons) args;
        return new Cons(reify(cons.car, newVars, boundVarTypes), reilist(cons.cdr, newVars, boundVarTypes));
    }

    // eval stuff

    private static IList bind(IList argNames, IList args, IList env)
    {
        if (length(argNames) != length(args))
        {
            throw new IllegalArgumentException("called bind with lists of different length");
        }
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        while (argNames != Nil.INSTANCE)
        {
            builder.put(((Cons) argNames).car, ((Cons) args).car);
            argNames = ((Cons) argNames).cdr;
            args = ((Cons) args).cdr;
        }
        ImmutableMap<ISExp, ISExp> frame = builder.build();
        IMap map;
        if (frame.isEmpty())
        {
            map = MNil.INSTANCE;
        }
        else
        {
            map = new Map(frame);
        }
        return new Cons(map, env);

    }

    static void addLabel(ImmutableMap.Builder<ISExp, ISExp> frame, ISExp key, ISExp value)
    {
        if(key instanceof Symbol)
        {
            Symbol name = (Symbol) key;
            frame.put(name, new Cons(labelSymbol, new Cons(value, Nil.INSTANCE)));
            return;
        }
        else if(key instanceof Cons && ((Cons) key).car instanceof Symbol)
        {
            Cons cons = (Cons) key;
            Symbol name = (Symbol) cons.car;
            IList args = cons.cdr;
            frame.put(name, new Cons(labelSymbol, new Cons(args, new Cons(value, Nil.INSTANCE))));
            return;
        }
        throw new IllegalArgumentException("can't label: " + key);
    }

    private ISExp unlabel(ISExp value, IList env)
    {
        if(value instanceof Cons && ((Cons) value).car.equals(labelSymbol))
        {
            if(length(value) == 3)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                Cons c3 = (Cons) c2.cdr;
                ISExp args = c2.car;
                ISExp body = c3.car;
                // function label
                if(args instanceof IList)
                {
                    return makeFunction((IList) args, body, env);
                }
            }
            else if (length(value) == 2)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                // reference label
                return eval(c2.car, env);
                /*if(c2.car instanceof Symbol)
                {
                    return lookup(env, (Symbol) c2.car);
                }*/
            }
        }
        return value;
    }

    private ISExp unlabelType(ISExp value, IList context, ImmutableSet<ISExp> boundVarTypes)
    {
        if(value instanceof Cons && ((Cons) value).car.equals(labelSymbol))
        {
            // FIXME
            if(length(value) == 3)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                Cons c3 = (Cons) c2.cdr;
                ISExp args = c2.car;
                ISExp body = c3.car;
                // function label
                if(args instanceof IList)
                {
                    return makeFunctionType((IList) args, body, context, boundVarTypes);
                }
            }
            else if (length(value) == 2)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                // reference label
                return infer(c2.car, context, boundVarTypes);
            }
        }
        return value.getType();
    }

    private ISExp lookup(IList env, Symbol name)
    {
        if (env == Nil.INSTANCE)
        {
            return Unbound.INSTANCE;
        }
        Cons cons = (Cons) env;
        if (cons.car == MNil.INSTANCE)
        {
            return lookup(cons.cdr, name);
        }
        if (cons.car instanceof Map)
        {
            Map map = (Map) cons.car;
            if (map.value.containsKey(name))
            {
                ISExp value = map.value.get(name);
                return unlabel(value, env);
            }
            return lookup(cons.cdr, name);
        }
        throw new IllegalArgumentException("lookup called with a list that has something other than a map:" + cons.car);
    }

    private ISExp beval(Cons args, IList env)
    {
        if(args.cdr == Nil.INSTANCE)
        {
            return eval(args.car, env);
        }
        ISExp definitions = args.car;
        if (definitions instanceof Map)
        {
            ImmutableMap.Builder<ISExp, ISExp> frameBuilder = ImmutableMap.builder();
            Map map = (Map) definitions;
            for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
            {
                addLabel(frameBuilder, entry.getKey(), entry.getValue());
            }
            return beval((Cons) args.cdr, new Cons(new Map(frameBuilder.build()), env));
        }
        else
        {
            if(definitions instanceof IList && length(definitions) == 2)
            {
                Cons c1 = (Cons) definitions;
                Cons c2 = (Cons) c1.cdr;
                if (c1.car.equals(makeSymbol("load")) && c2.car instanceof StringAtom)
                {
                    String location = ((StringAtom) c2.car).value;
                    ISExp frameExp = read(location);
                    if (!(frameExp instanceof IMap))
                    {
                        throw new IllegalArgumentException("load called from bind should return map, got: " + frameExp);
                    }
                    if(frameExp == MNil.INSTANCE)
                    {
                        return beval((Cons) args.cdr, env);
                    }
                    return beval((Cons) args.cdr, new Cons(loadFrame((Map) frameExp), env));
                }
            }
        }
        throw new IllegalArgumentException("bind enviroments have to be either maps or load expressions.");
    }

    private ISExp evSymbol(Symbol symbol, IList env)
    {
        ISExp value = lookup(env, symbol);
        if(value == Unbound.INSTANCE)
        {
            throw new IllegalStateException("Undefined variable " + symbol);
        }
        return value;
    }

    @SuppressWarnings("StringEquality")
    private ISExp eval(ISExp exp, IList env)
    {
        if (exp instanceof Symbol)
        {
            return evSymbol((Symbol) exp, env);
        }
        else if (exp instanceof ArithmSymbol)
        {
            return new ArithmOp((ArithmSymbol) exp);
        }
        else if (exp instanceof IAtom)
        {
            return exp;
        }
        else if (exp instanceof Cons)
        {
            Cons cons = (Cons) exp;
            if (cons.car instanceof Symbol)
            {
                String name = ((Symbol) cons.car).value;
                if (name == "quote")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("quote needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return cdr.car;
                }
                else if (name == "lambda")
                {
                    if (length(cons.cdr) != 2)
                    {
                        throw new IllegalArgumentException("lambda needs 2 arguments, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    if (!(c2.car instanceof IList))
                    {
                        throw new IllegalArgumentException("lambda needs a list as a first argument, got: " + c2.car);
                    }
                    IList args = (IList) c2.car;
                    ISExp body = c3.car;
                    return makeFunction(args, body, env);
                }
                else if (name == "bind")
                {
                    if (cons.cdr == Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("bind needs at least 1 argument");
                    }
                    Cons args = (Cons) cons.cdr;
                    return beval(args, env);
                }
                /*else if (name == "macro")
                {
                    // TODO
                    if (length(cons.cdr) != 3)
                    {
                        throw new IllegalArgumentException("macro needs 3 arguments, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    Cons c4 = (Cons) c3.cdr;
                    makeMacro(c2.car, c3.car, c4.car);
                }*/
                // TODO: macro?
                else if(name == "delay")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("delay needs 1 argument, got: " + exp);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return eval(delay(cdr.car), env);
                }
                // TODO: macro?
                else if(name == "delay_values")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("delay_values needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    if (!(cdr.car instanceof IMap))
                    {
                        throw new IllegalArgumentException("delay_values needs a map as a first argument, got: " + cdr.car);
                    }
                    if(cdr.car == MNil.INSTANCE)
                    {
                        return MNil.INSTANCE;
                    }
                    Map map = (Map) cdr.car;
                    ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                    for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
                    {
                        builder.put(entry.getKey(), delay(entry.getValue()));
                    }
                    return eval(new Map(builder.build()), env);
                }
                // TODO: macro? primitive? primitive macro?
                else if(name == "fold")
                {
                    Cons c1 = (Cons) evlis(cons.cdr, env);
                    Cons c2 = (Cons) c1.cdr;
                    Cons c3 = (Cons) c2.cdr;

                    ISExp f = c1.car;
                    ISExp ret = c2.car;
                    IList list = (IList) c3.car;
                    while(list != Nil.INSTANCE)
                    {
                        Cons c4 = (Cons) list;
                        ret = apply(f, new Cons(ret, new Cons(c4.car, Nil.INSTANCE)));
                        list = c4.cdr;
                    }
                    return ret;
                }
                ISExp func = lookup(env, (Symbol) cons.car);
                if(isMacro(func))
                {
                    eval(expandMacro(func, cons.cdr), env);
                }
            }
            ISExp func = eval(cons.car, env);
            IList args = evlis(cons.cdr, env);
            return apply(func, args);
        }
        else if(exp instanceof Map)
        {
            Map map = (Map) exp;
            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
            for(java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
            {
                builder.put(eval(entry.getKey(), env), eval(entry.getValue(), env));
            }
            return new Map(builder.build());
        }
        throw new IllegalStateException("eval of " + exp);
    }

    private ISExp inferSymbol(Symbol symbol, IList context, ImmutableSet<ISExp> boundVarTypes)
    {
        ISExp type = lookup(context, symbol);
        if(type == Unbound.INSTANCE)
        {
            throw new IllegalStateException("Undefined variable type " + symbol);
        }
        return reify(type, Maps.<ISExp, ISExp>newHashMap(), boundVarTypes);
    }
    @SuppressWarnings("StringEquality")
    private ISExp infer(ISExp exp, IList context, ImmutableSet<ISExp> boundVarTypes)
    {
        if (exp instanceof Symbol)
        {
            return inferSymbol((Symbol) exp, context, boundVarTypes);
        }
        else if (exp instanceof ArithmSymbol)
        {
            return ArithmOp.makeArithmType((ArithmSymbol) exp);
        }
        else if (exp instanceof IAtom)
        {
            return exp.getType();
        }
        else if (exp instanceof Cons)
        {
            Cons cons = (Cons) exp;
            if (cons.car instanceof Symbol)
            {
                String name = ((Symbol) cons.car).value;
                if (name == "quote")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("quote needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return cdr.car.getType();
                }
                else if (name == "lambda")
                {
                    if (length(cons.cdr) != 2)
                    {
                        throw new IllegalArgumentException("lambda needs 2 arguments, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    if (!(c2.car instanceof IList))
                    {
                        throw new IllegalArgumentException("lambda needs a list as a first argument, got: " + c2.car);
                    }
                    IList args = (IList) c2.car;
                    ISExp body = c3.car;
                    return makeFunctionType(args, body, context, boundVarTypes);
                }
                else if (name == "bind")
                {
                    if (cons.cdr == Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("bind needs at least 1 argument");
                    }
                    Cons args = (Cons) cons.cdr;
                    return binfer(args, context, boundVarTypes);
                }
                // dirty hax
                else if (name == "list")
                {
                    return PrimTypes.List.type;
                }
                else if (name == "map")
                {
                    return PrimTypes.Map.type;
                }
                // even dirtier hax
                else if (name == "car")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("car needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    unifier.unify(infer(cdr.car, context, boundVarTypes), PrimTypes.List.type);
                    return unifier.getFreshVar();
                }
                else if (name == "carm")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("car needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    unifier.unify(infer(cdr, context, boundVarTypes), PrimTypes.List.type);
                    return unifier.getFreshVar();
                }
                else if (name == "delay")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("delay needs 1 argument, got: " + exp);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return absType(ImmutableList.<ISExp>of(), infer(cdr.car, context, boundVarTypes));
                }
                else if (name == "delay_values")
                {
                    return PrimTypes.Map.type;
                }
                else if(name == "fold")
                {
                    if (length(cons.cdr) != 3)
                    {
                        throw new IllegalArgumentException("folds needs 3 arguments, got: " + exp);
                    }
                    Cons c1 = (Cons) cons.cdr;
                    Cons c2 = (Cons) c1.cdr;
                    Cons c3 = (Cons) c2.cdr;

                    ISExp f = infer(c1.car, context, boundVarTypes);
                    ISExp z = infer(c2.car, context, boundVarTypes);
                    ISExp list = infer(c3.car, context, boundVarTypes);
                    // ((A, B) -> B, A, list[A]) -> B
                    ISExp A = unifier.getFreshVar(), B = unifier.getFreshVar();
                    ISExp fType = absType(ImmutableList.of(A, B), B);
                    unifier.unify(f, fType);
                    unifier.unify(z, B);
                    unifier.unify(list, PrimTypes.List.type);
                    return B;
                }
            }
            ISExp funcType = infer(cons.car, context, boundVarTypes);
            IList args = cons.cdr;
            ImmutableList.Builder<ISExp> argTypesBuilder = ImmutableList.builder();
            while(args != Nil.INSTANCE)
            {
                Cons c2 = (Cons) args;
                argTypesBuilder.add(infer(c2.car, context, boundVarTypes));
                args = c2.cdr;
            }
            ISExp bodyType = unifier.getFreshVar();
            unifier.unify(funcType, absType(argTypesBuilder.build(), bodyType));
            return bodyType;
        }
        else if(exp instanceof Map)
        {
            return PrimTypes.Map.type;
        }
        throw new IllegalStateException("infer of " + exp);
    }

    private ISExp apply(ISExp func, IList args)
    {
        if (func instanceof ICallableExp)
        {
            return ((ICallableExp) func).apply(args);
        }
        if (func instanceof Cons)
        {
            Cons c1 = (Cons) func;
            if ((functionSymbol.equals(c1.car) || macroSymbol.equals(c1.car)) && c1.cdr != Nil.INSTANCE)
            {
                Cons c2 = (Cons) c1.cdr;
                if (c2.car instanceof IList && c2.cdr != Nil.INSTANCE)
                {
                    IList argNames = (IList) c2.car;
                    Cons c3 = (Cons) c2.cdr;
                    ISExp body = c3.car;
                    if (c3.cdr != Nil.INSTANCE)
                    {
                        Cons c4 = (Cons) c3.cdr;
                        ISExp env = c4.car;
                        if (env instanceof IList && c4.cdr == Nil.INSTANCE)
                        {
                            return eval(body, bind(argNames, args, (IList) env));
                        }
                    }
                }
            }
        }
        if (func instanceof Map)
        {
            Map map = (Map) func;
            if(length(args) != 1)
            {
                throw new IllegalArgumentException("Can only apply map to 1 argument");
            }
            Cons c1 = (Cons) args;
            if(map.value.containsKey(c1.car))
            {
                return map.value.get(c1.car);
            }
            throw new IllegalStateException("Map " + map + " doesn't have key " + c1.car);
        }
        throw new IllegalArgumentException("Don't know how to apply: " + func);
    }

    // inter stuff

    private ISExp binfer(Cons args, IList context, ImmutableSet<ISExp> boundVarTypes)
    {
        if(args.cdr == Nil.INSTANCE)
        {
            return infer(args.car, context, boundVarTypes);
        }
        ISExp definitions = args.car;

        IMap defs = null;

        if (definitions instanceof Map)
        {
            defs = (Map) definitions;
        }
        else
        {
            if(definitions instanceof IList && length(definitions) == 2)
            {
                Cons c1 = (Cons) definitions;
                Cons c2 = (Cons) c1.cdr;
                if (c1.car.equals(makeSymbol("load")) && c2.car instanceof StringAtom)
                {
                    String location = ((StringAtom) c2.car).value;
                    ISExp frameExp = read(location);
                    if (!(frameExp instanceof IMap))
                    {
                        throw new IllegalArgumentException("load called from bind should return map, got: " + frameExp);
                    }
                    defs = (IMap) frameExp;
                }
            }
        }
        if(defs == MNil.INSTANCE)
        {
            return binfer((Cons) args.cdr, context, boundVarTypes);
        }
        if(defs != null)
        {
            return binfer((Cons) args.cdr, frameToContext(loadFrame((Map) defs), context, boundVarTypes), boundVarTypes);
        }
        throw new IllegalArgumentException("bind enviroments have to be either direct maps or direct load expressions.");
    }

    private IList frameToContext(Map frame, IList context, ImmutableSet<ISExp> boundVarTypes)
    {
        java.util.Map<ISExp, ISExp> ctxBuilder = Maps.newHashMap();
        ImmutableSet.Builder<ISExp> builder = ImmutableSet.builder();
        builder.addAll(boundVarTypes);
        for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : frame.value.entrySet())
        {
            // new ones shadow old ones. TODO: unify with the list approach?
            ISExp type = unifier.getFreshVar();
            ctxBuilder.put((Symbol) entry.getKey(), type);
            builder.add(type);
        }
        IList newContext = new Cons(new Map(ImmutableMap.copyOf(ctxBuilder)), context);
        ImmutableSet<ISExp> newBoundVarTypes = builder.build();
        for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : frame.value.entrySet())
        {
            unifier.unify(ctxBuilder.get(entry.getKey()), unlabelType(entry.getValue(), newContext, newBoundVarTypes));
            //inferBindPair(ctxBuilder, context, vars, entry.getKey(), entry.getValue());
        }
        return newContext;
    }

    private IList envToContext(IList env, ImmutableSet<ISExp> boundVarTypes)
    {
        if (env == Nil.INSTANCE)
        {
            return Nil.INSTANCE;
        }
        Cons cons = (Cons) env;
        return frameToContext((Map) cons.car, envToContext(cons.cdr, boundVarTypes), boundVarTypes);
    }

    private ISExp reify(ISExp type, java.util.Map<ISExp, ISExp> newVars, ImmutableSet<ISExp> boundVarTypes)
    {
        type = unifier.find(type);
        if(type instanceof Symbol)
        {
            if(!boundVarTypes.contains(type))
            {
                if(!newVars.containsKey(type))
                {
                    newVars.put(type, unifier.getFreshVar());
                }
                return newVars.get(type);
            }
        }
        if(type instanceof IList)
        {
            return reilist((IList) type, newVars, boundVarTypes);
        }
        return type;
    }

    private ISExp makeFunctionType(IList args, ISExp body, IList env, ImmutableSet<ISExp> boundVarTypes)
    {
        if(args == Nil.INSTANCE)
        {
            return absType(ImmutableList.<ISExp>of(), infer(body, env, boundVarTypes));
        }
        java.util.Map<ISExp, ISExp> frame = Maps.newHashMap();
        ImmutableList.Builder<ISExp> argTypesBuilder = ImmutableList.builder();
        ImmutableSet.Builder<ISExp> builder = ImmutableSet.builder();
        builder.addAll(boundVarTypes);
        while(args != Nil.INSTANCE)
        {
            Cons c4 = (Cons) args;
            Symbol arg = (Symbol) c4.car;
            ISExp argType = unifier.getFreshVar();
            frame.put(arg, argType);
            argTypesBuilder.add(argType);
            builder.add(argType);
            args = c4.cdr;
        }
        ISExp bodyType = infer(body, new Cons(new Map(ImmutableMap.copyOf(frame)), env), builder.build());
        return absType(argTypesBuilder.build(), bodyType);
    }

    // macro
    private boolean isMacro(ISExp func)
    {
        if (func instanceof Cons)
        {
            Cons c1 = (Cons) func;
            if (macroSymbol.equals(c1.car) && c1.cdr != Nil.INSTANCE)
            {
                // not checking further, assuming macroSymbol + length are enough
                return true;
            }
        }
        return false;
    }

    private ISExp expandMacro(ISExp func, IList args)
    {
        return apply(func, args);
    }

    private static ISExp delay(ISExp exp)
    {
        return new Cons(makeSymbol("lambda"), new Cons(Nil.INSTANCE, new Cons(exp, Nil.INSTANCE)));
    }

}
